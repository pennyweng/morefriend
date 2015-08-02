package controllers.line

import play.api._
import play.api.mvc._
import play.Logger

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.collection.JavaConversions._

import akka.actor.{ActorSystem, Props}

import redis.RedisServer
import redis.RedisClientPool
import redis.api.Limit
import redis.RedisClient
import play.api.libs.concurrent.Akka
import scala.concurrent.Future

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._

import scala.util.parsing.json.JSONFormat
import controllers.line.Constants._
import controllers.line.BlockApplication.REDIS_KEY_BLOCK
import controllers.line.TraceApplication._

import models.Notification

case class LineMessage @JsonCreator() (
	@scala.beans.BeanProperty @JsonProperty("msg") msg : String, 
	@scala.beans.BeanProperty @JsonProperty("from") from : String,
	@scala.beans.BeanProperty @JsonProperty("fromLid") fromLid : String,
	@scala.beans.BeanProperty @JsonProperty("to") to : String,
	@scala.beans.BeanProperty @JsonProperty("toLid") toLid : String,
	@scala.beans.BeanProperty @JsonProperty("ts") ts : Long		
)

object MessageApplication  extends Controller {
	implicit val system = Akka.system
	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))
	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
	
	val REDIS_KEY_MSG = "LMSG"
	val REDIS_KEY_NOTIFICATION = "NOTIFY"

	val REDIS_KEY_NEW_MSG = "NMSG:"
	val REDIS_KEY_NEW_MSG_NOTI = "NMSGN"
	val REDIS_KEY_NEW_MSG_NOTI_LAST_U = "NMSGNU"	


	def putNewMsg(fromUid : String, fromLid : String, toUid: String, toLid: String, msg : String) = Action {
		putNewMsg1(fromUid, fromLid, toUid, toLid, msg)
		Ok("")
	}

	def putNewMsg1(fromUid : String, fromLid : String, toUid: String, toLid: String, msg : String) {
		Logger.info(s"putNewMsg fromUid:${fromUid}, fromLid:${fromLid}, toUid:${toUid}, toLid:${toLid}, msg:${msg}")

		val now = System.currentTimeMillis
		val lmsg = LineMessage(msg, fromUid, fromLid, toUid, toLid, now)
		updateHot(toUid)
		updateOnline(fromUid)
		updateActive(fromUid)

		// from part
		redis.hget(REDIS_KEY_NEW_MSG + fromUid , toUid).map{ ff => // Future[Option[R]]
			if(ff.isDefined) {
				val lms = mapper.readValue[Array[LineMessage]](ff.get.utf8String, classOf[Array[LineMessage]])
				val newLms = lms ++ Array(lmsg) 
				val newLmsn = if(newLms.length > 100) newLms.drop(newLms.length - 100) else newLms
				redis.hset(REDIS_KEY_NEW_MSG + fromUid, toUid, mapper.writeValueAsString(newLmsn))
			} else {
				redis.hset(REDIS_KEY_NEW_MSG + fromUid, toUid, mapper.writeValueAsString(Array(lmsg)))
			} 
		}

		// from part old function
		redis.hget(REDIS_KEY_MSG, fromUid).map{ ff => // Future[Option[R]]
			if(ff.isDefined) {
				val lms = mapper.readValue[Array[LineMessage]](ff.get.utf8String, classOf[Array[LineMessage]])
				val newLms = Array(lmsg) ++ lms
				val newLmsn = if(newLms.length > 100) newLms.take(100) else newLms  
				redis.hset(REDIS_KEY_MSG, fromUid, mapper.writeValueAsString(newLmsn))
			} else {
				redis.hset(REDIS_KEY_MSG, fromUid, mapper.writeValueAsString( 
					Array(LineMessage(msg, fromUid, fromLid, toUid, toLid, now))))
			} 
		}

		redis.hget(REDIS_KEY_BLOCK, toUid).map { nn =>
			if(nn.isDefined && nn.get.utf8String.indexOf(fromUid) != -1) {
				Logger.info(s"toUid ${toUid} block fromUid ${fromUid}")
			} else {
				// to part new function
				for{
					ff <- redis.hget(REDIS_KEY_NEW_MSG + toUid, fromUid)
					kk <- saveToMsg(lmsg, ff)
					cc <- sendRealNotice(lmsg)// redis.hset(REDIS_KEY_NEW_MSG_NOTI_LAST_U, toUid, mapper.writeValueAsString(lmsg))
					dd <- saveNewNotification(fromUid, toUid)
					ss <- redis.hset(REDIS_KEY_NOTIFICATION, toUid, now + "##" + fromUid)
				}{}

				// to part old function
				for{
					ff <- redis.hget(REDIS_KEY_MSG, toUid)
					kk <- saveOldToMsg(lmsg, toUid, ff)
					// cc <- redis.hset(REDIS_KEY_NOTIFICATION, toUid, now)
				} {}
			}
		}
	}

	private def sendRealNotice(lmsg : LineMessage) = {
		redis.hget(REDIS_KEY_GCM, lmsg.to).map { gcm =>
			if(gcm.isDefined) {
				Logger.debug("the user has gcm." + lmsg.to )
				val imgUrl = "http://www.jookershop.com:9001/linefriend/account/image?uid=" + lmsg.from
				val mm = lmsg.fromLid + "傳訊息給您##"+ imgUrl
				Notification.sendLineFriend( List(gcm.get.utf8String),mm )
			} else {
				Logger.debug("the user has no gcm." + lmsg.to )
				redis.hset(REDIS_KEY_NEW_MSG_NOTI_LAST_U, lmsg.to, mapper.writeValueAsString(lmsg))
			}
		}
	}

	private def saveNewNotification(fromUid : String, toUid : String) = {
		for{
			ff <- redis.hget(REDIS_KEY_NEW_MSG_NOTI, toUid)
			vv <- if(ff.isDefined) {
				val lms = mapper.readValue[Array[String]](ff.get.utf8String, classOf[Array[String]])
				val newLms = Array(fromUid) ++ lms 
				redis.hset(REDIS_KEY_NEW_MSG_NOTI, toUid, mapper.writeValueAsString(newLms.distinct))
			} else {
				redis.hset(REDIS_KEY_NEW_MSG_NOTI, toUid, mapper.writeValueAsString( 
					Array(fromUid)))
			} 			
		} yield {
			vv
		}
	}

	private def saveOldToMsg(lmsg : LineMessage, toUid : String,  ff : Option[akka.util.ByteString]) : scala.concurrent.Future[Boolean] = {
		if(ff.isDefined) {
			val lms = mapper.readValue[Array[LineMessage]](ff.get.utf8String, classOf[Array[LineMessage]])
			val newLms = Array(lmsg) ++ lms 
			val newLmsn = if(newLms.length > 100) newLms.take(100) else newLms  
			redis.hset(REDIS_KEY_MSG, toUid, mapper.writeValueAsString(newLmsn))
		} else {
			redis.hset(REDIS_KEY_MSG, toUid, mapper.writeValueAsString(Array(lmsg)))
		} 
	}

	private def saveToMsg(lmsg : LineMessage, ff : Option[akka.util.ByteString]) : scala.concurrent.Future[Boolean] = {
		if(ff.isDefined) {
			val lms = mapper.readValue[Array[LineMessage]](ff.get.utf8String, classOf[Array[LineMessage]])
			val newLms = lms ++ Array(lmsg) 
			val newLmsn = if(newLms.length > 100) newLms.drop(newLms.length - 100) else newLms
			redis.hset(REDIS_KEY_NEW_MSG + lmsg.to, lmsg.from, mapper.writeValueAsString(newLmsn))
		} else {
			redis.hset(REDIS_KEY_NEW_MSG + lmsg.to, lmsg.from, mapper.writeValueAsString( 
				Array(lmsg)))
		}		
	}

	def delNewMsg(uid : String, toUid : String) = Action {
		redis.hdel(REDIS_KEY_NEW_MSG+ uid, toUid)
		Ok("")
	}

	def getNewMsg(uid : String, toUid : String) = Action.async {
		Logger.info(s"getNewMsg uid ${uid}, toUid:${toUid}")
		updateHot(toUid)
		updateOnline(uid)
		updateActive(uid)

		redis.hdel(REDIS_KEY_NOTIFICATION, uid)

		redis.hget(REDIS_KEY_NEW_MSG + uid, toUid).map{ ff => // Future[Option[R]]
			redis.hget(REDIS_KEY_NEW_MSG_NOTI, uid).map { kk => // Future[Option[R]]
				if(kk.isDefined) {	
					val lms = mapper.readValue[Array[String]](kk.get.utf8String, classOf[Array[String]]).filter( _ != toUid)
					if(lms.size > 0) {
 						redis.hset(REDIS_KEY_NEW_MSG_NOTI, uid, mapper.writeValueAsString(lms.distinct))
					} else {
						redis.hdel(REDIS_KEY_NEW_MSG_NOTI, uid) 
					}
				} //else Logger.info(s"REDIS_KEY_NEW_MSG_NOTI no data ${uid}")
			}	

			Ok(if(ff.isDefined) {
				ff.get.utf8String
			} else "[]")
		}
	}	

	def getNewMsgUsers(uid : String) = Action.async {
		Logger.info(s"getNewMsgUsers uid:${uid}")
		updateOnline(uid)
		updateActive(uid)

		redis.hdel(REDIS_KEY_NOTIFICATION, uid)
		redis.hdel(REDIS_KEY_NEW_MSG_NOTI_LAST_U, uid)

		for {
			bb <- redis.hgetall(REDIS_KEY_NEW_MSG + uid) //Future[Map[String, R]]
			ss <- redis.hkeys(REDIS_KEY_NEW_MSG + uid) //Future[Seq[String]]
			nn <- redis.hget(REDIS_KEY_NEW_MSG_NOTI, uid) // Future[Option[R]]
			h <- if(ss.length > 0) redis.hmget(REDIS_KEY_USER, ss.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val userss = if(nn.isDefined) nn.get.utf8String else ""
			Logger.info(s"userss:${userss}")

			val resp = h.flatten.map { ee =>
				val s = ee.utf8String
				val tuid = if(s.indexOf("uid\":") != -1 && s.indexOf(",") != -1) s.substring(s.indexOf("uid\":")+ 5, s.indexOf(",")).replace("\"","") else s
				val lastWord = if(bb.contains(tuid)) {
					val sv = mapper.readValue[Array[LineMessage]](bb(tuid).utf8String, classOf[Array[LineMessage]]) 
					if(sv.length >0) sv(sv.length-1).msg else ""
				} else ""

				if(userss.indexOf(tuid) != -1) {
					s"""{"userdata":${s}, "has_noti":true, "last_word":"${lastWord}"}"""
				} else {
					s"""{"userdata":${s}, "has_noti":false, "last_word":"${lastWord}" }"""
				}
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}


	def getNofiticationUsers(uid : String) = Action.async {
		Logger.info(s"getNofiticationUsers uid:${uid}")

		for {
			nn <- redis.hget(REDIS_KEY_NEW_MSG_NOTI_LAST_U, uid) // Future[Option[R]]
			cc <- checkuser(nn)
		} yield {
			val userss = if(nn.isDefined && cc) 
				nn.get.utf8String
			else "{}"
			redis.hdel(REDIS_KEY_NEW_MSG_NOTI_LAST_U, uid)
			Ok(userss)
		}
	}

	def checkuser(nn : Option[akka.util.ByteString]) = {
		if(nn.isDefined){
			val lms = mapper.readValue[LineMessage](nn.get.utf8String, classOf[LineMessage])
			val uuid = lms.from
			redis.hexists(REDIS_KEY_USER, uuid)
		} else {
			Future.successful(false)
		}
	}


	def delAllMsg(uid : String ) = Action {
		updateOnline(uid)		
		redis.hdel(REDIS_KEY_MSG, uid)
		redis.hdel(REDIS_KEY_NOTIFICATION, uid)
		Ok("")
	}

	def getMsg(uid : String) = Action.async {
		updateOnline(uid)
		updateActive(uid)
			
		redis.hdel(REDIS_KEY_NOTIFICATION, uid)
		redis.hget(REDIS_KEY_MSG, uid).map{ ff => // Future[Option[R]]
			Ok(if(ff.isDefined) {
				ff.get.utf8String
			} else "[]")
		}
	}

	def sendAll(uid : String, msg : String) = Action {
		if(uid == "546D80E903AF643AA7FCD6292AA2DD26B84BFE4B") {
			redis.hkeys(REDIS_KEY_USER).map { keys => // Future[Seq[String]]
				keys.map { toUid =>
					redis.hget(REDIS_KEY_USER, toUid).map { userData => // Future[Option[R]]
						if(userData.isDefined) {
							val tt = mapper.readTree(userData.get.utf8String)
							val toLid = tt.get("lid").asText
							putNewMsg1(uid, "系統管理員", toUid, toLid, msg)
						}
					}
				}
			}
			Ok("")
		} else Ok("fail")
	}

	def sendOne(uid : String, toUid: String, msg : String) = Action {
		if(uid == "546D80E903AF643AA7FCD6292AA2DD26B84BFE4B") {
			redis.hget(REDIS_KEY_USER, toUid).map { userData => // Future[Option[R]]
				if(userData.isDefined) {
					val tt = mapper.readTree(userData.get.utf8String)
					val toLid = tt.get("lid").asText
					putNewMsg1(uid, "系統管理員", toUid, toLid, msg)
				}
			}
			Ok("")
		} else Ok("fail")
	}

	def getAdminMsg(uid : String, ver : String) = Action.async {
		redis.get(REDIS_KEY_ADMIN_MSG).map { ss => // Future[Option[R]]
			if(ss.isDefined) {
				val data = ss.get.utf8String
				val version = data.split("##")(0)
				if(version == ver) Ok("")
				else Ok(data)
			} else {
				Ok("")	
			}
		}
	}
}