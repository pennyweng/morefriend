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

object TraceApplication extends Controller {
	implicit val system = Akka.system
	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val chatPath = "./chat"

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	def addTraceUser(uid : String, toUid: String) = Action {
		Logger.info(s"addTraceUser uid:${uid}, toUid:${toUid}")
		updateOnline(uid)		

		redis.hget(REDIS_KEY_TRACE, uid).map{ ff => // Future[Option[R]]
			if(ff.isDefined) {
				if(ff.get.utf8String.indexOf(toUid) == -1)
				redis.hset(REDIS_KEY_TRACE, uid, ff.get.utf8String + ","  + toUid)
			} else {
				redis.hset(REDIS_KEY_TRACE, uid, toUid)
			} 
		}
		Ok("")			
	}

	def getTraceUser(uid : String) = Action.async {
		Logger.info(s"getTraceUser uid:${uid}")
		updateOnline(uid)
		val now = System.currentTimeMillis

		for {
			nn <- redis.hget(REDIS_KEY_TRACE, uid) // Future[Option[R]]
			h <- if(nn.isDefined && nn.get.utf8String.length > 0) redis.hmget(REDIS_KEY_USER, nn.get.utf8String.split(",").toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}

	def delTraceUser(uid: String, toUid : String ) = Action {
		redis.hget(REDIS_KEY_TRACE, uid).map{ ff => // Future[Option[R]]
			if(ff.isDefined) {
				redis.hset(REDIS_KEY_TRACE, uid, ff.get.utf8String.split(",").filter( _ != toUid).mkString(","))
			} 
		}
		Ok("")
	}

	def updateOnline( uid : String ) {
		if(uid != "") {
			redis.hexists(REDIS_KEY_HIDE, uid).map { isHide =>
				if(!isHide) redis.zadd(REDIS_KEY_ONLINE, (System.currentTimeMillis, uid))
			}
		}
	}

	def updateHot(uid : String) {
		if(uid != "") {
			redis.hexists(REDIS_KEY_HIDE, uid).map { isHide =>
				if(!isHide) redis.zincrby(REDIS_KEY_HOT_USER, 1.toDouble, uid)
			}
		}
	}

	def updateActive(uid : String) {
		if(uid != "") {
			redis.hexists(REDIS_KEY_HIDE, uid).map { isHide =>
				if(!isHide) redis.zincrby(REDIS_KEY_ACTIVE_USER, 1.toDouble, uid)
			}
		}
	}

	def getChatroom() = Action.async {
		//Future[Map[String, R]]
		redis.hgetall(REDIS_KEY_CHAT).map { ee =>
			Ok(ee.map { k =>
				s""""${k._2.utf8String}" """
			}.mkString("[",",", "]"))
		}
	}

	def createChatRoom = {
		redis.hset(REDIS_KEY_CHAT, "1", "1@電影聊天室@0@0@#858A3D@ahappychat@0")
		redis.hset(REDIS_KEY_CHAT, "2", "2@美食聊天室@0@0@#858A3D@ahappychat@0")
		redis.hset(REDIS_KEY_CHAT, "3", "3@打屁聊天室@26@8@#858A3D@ahappychat@1")
		redis.hset(REDIS_KEY_CHAT, "4", "4@北部人聊天室@0@0@#DEAE2C@ahappychat@0")
		redis.hset(REDIS_KEY_CHAT, "5", "5@中部人聊天室@0@0@#DEAE2C@ahappychat@0")
		redis.hset(REDIS_KEY_CHAT, "6", "6@南部人聊天室@0@0@#DEAE2C@ahappychat@0")
		redis.hset(REDIS_KEY_CHAT, "7", "7@20歲聊天室@0@0@#009989@ahappychat@0")
		redis.hset(REDIS_KEY_CHAT, "8", "8@25歲聊天室@0@0@#009989@ahappychat@0")
		redis.hset(REDIS_KEY_CHAT, "9", "9@35歲聊天室@0@0@#009989@ahappychat@0")
	}

	def getNotify( uid : String ) = Action.async {
		 // Future[Option[R]]
		for { 
			no <- redis.hget(REDIS_KEY_NOTIFICATION, uid)
			uu <- checkuser(no, uid)
		} yield {
			val nn = if(no.isDefined && uu) s"""{"n":{"command":"GET_NOTIFICATION","ts":"${no.get.utf8String}" }}"""  else s"""{"n":null}"""
			Ok(nn)
		}
		// redis.hget(REDIS_KEY_NOTIFICATION, uid).map { no =>
		// 	val nn = if(no.isDefined) s"""{"n":{"command":"GET_NOTIFICATION","ts":${no.get.utf8String}}}"""  else s"""{"n":null}"""
		// 	Ok(nn)
		// }
	}

	def checkuser( nn : Option[akka.util.ByteString], uid : String ) = {
		if(nn.isDefined){
			val k = nn.get.utf8String
			if(k.indexOf("##") != -1) {
				val uid = k.split("##")(1)
				redis.hexists(REDIS_KEY_USER, uid) //Future[Boolean]
			} else {
				redis.hdel(REDIS_KEY_NOTIFICATION, uid)
				Future.successful(false)
			}
		} else {
			redis.hdel(REDIS_KEY_NOTIFICATION, uid)
			Future.successful(false)
		}
	}
	def getChatImg( id : String) = Action {
		val path = chatPath + "/" + id + ".png"
		val ff = new java.io.File(path)
  		if(ff.exists)
  			Ok.sendFile(ff)
  		else NotFound
	}	


	def getOnlineUser(uid : String) = Action.async {
		Logger.info(s"getOnlineUser uid:${uid}")
		updateOnline(uid)
		val now = System.currentTimeMillis
		val start = Limit(now - (15 * 60 * 1000l))
		val end = Limit(now)

		redis.zremrangebyscore(REDIS_KEY_ONLINE, Limit(0.toDouble), start)

		for {
			ut <- redis.zrevrangebyscore(REDIS_KEY_ONLINE, end, start) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_USER, ut.map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}


	def delActiveHotUsers() {
		redis.del(REDIS_KEY_HOT_USER)
		redis.del(REDIS_KEY_ACTIVE_USER)
	}

	def getHotUser(uid : String) = Action.async {
		Logger.info(s"getHotUser uid:${uid}")
		updateOnline(uid)
		val start = Limit(0)
		val end = Limit(10000000)

		// redis.zremrangebyscore(REDIS_KEY_ONLINE, Limit(0.toDouble), start)

		for {
			ut <- redis.zrevrangebyscore(REDIS_KEY_HOT_USER, end, start) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_USER, ut.take(50).map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}

	def getActiveUser(uid : String) = Action.async {
		Logger.info(s"getActiveUser uid:${uid}")
		updateOnline(uid)
		val start = Limit(0)
		val end = Limit(10000000)

		for {
			ut <- redis.zrevrangebyscore(REDIS_KEY_ACTIVE_USER, end, start) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_USER, ut.take(50).map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}

	def getTodayUser(uid : String) = Action.async {
		Logger.info(s"getTodayUser uid:${uid}")
		updateOnline(uid)
		val now = System.currentTimeMillis
		val start = Limit(now - 86400000)
		val end = Limit(now)

		redis.zremrangebyscore(REDIS_KEY_TODAY_USER, Limit(0.toDouble), start)

		for {
			ut <- redis.zrevrangebyscore(REDIS_KEY_TODAY_USER, end, start) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_USER, ut.take(50).map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}



}