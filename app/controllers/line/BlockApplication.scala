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
import scala.collection.mutable.ListBuffer
import controllers.line.Constants._

object BlockApplication extends Controller {
	implicit val system = Akka.system

	val REDIS_KEY_BLOCK = "BLOCK"
	val REDIS_KEY_USER = "USR"
	val REDIS_KEY_ADMIN_BLOCK = "ABLOCK"
	val REDIS_KEY_ADMIN_BLOCK_LINE = "ABLOCKL"	
	val REDIS_KEY_REPORT_BLOCK = "RB"
	val REDIS_KEY_REPORT_BLOCK_U = "RBU"

	val LOCK_TIME = 86400000 * 20l
	
	val redis = RedisClient(REDIS_HOST, 6379)
	val blockUsers = ListBuffer[String]()
	val blockLines = ListBuffer[String]()
	val mapper = new ObjectMapper()
	
	def addBlockUser(uid : String, toUid: String) = Action {
		Logger.info(s"addBlockUser uid:${uid}, toUid:${toUid}")
		controllers.line.TraceApplication.updateOnline(uid)		

		redis.hget(REDIS_KEY_BLOCK, uid).map{ ff => // Future[Option[R]]
			if(ff.isDefined) {
				if(ff.get.utf8String.indexOf(toUid) == -1)
				redis.hset(REDIS_KEY_BLOCK, uid, ff.get.utf8String + ","  + toUid)
			} else {
				redis.hset(REDIS_KEY_BLOCK, uid, toUid)
			} 
		}
		Ok("")			
	}

	def getBlockUser(uid : String) = Action.async {
		Logger.info(s"getTraceUser uid:${uid}")
		controllers.line.TraceApplication.updateOnline(uid)
		val now = System.currentTimeMillis

		for {
			nn <- redis.hget(REDIS_KEY_BLOCK, uid) // Future[Option[R]]
			h <- if(nn.isDefined && nn.get.utf8String.length > 0) redis.hmget(REDIS_KEY_USER, nn.get.utf8String.split(",").toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}
	}

	def delBlockUser(uid: String, toUid : String ) = Action {
		Logger.info(s"del block user uid:${uid} to ${toUid}")
		controllers.line.TraceApplication.updateOnline(uid)

		redis.hget(REDIS_KEY_BLOCK, uid).map{ ff => // Future[Option[R]]
			if(ff.isDefined) {
				redis.hset(REDIS_KEY_BLOCK, uid, ff.get.utf8String.split(",").filter( _ != toUid).mkString(","))
			} 
		}
		Ok("")
	}

	def refreshAdminBlock() {
		Logger.info(s"refreshAdminBlock")
		val now = System.currentTimeMillis
		redis.hgetall(REDIS_KEY_ADMIN_BLOCK).map { ee =>
			ee.map { k =>
				val unLockTime = k._2.utf8String.split(",")(0).toLong
				// if( now > unLockTime) {
				// 	println("refreshAdminBlock unlock blockUsers" + k._1)
				// 	redis.hdel(REDIS_KEY_ADMIN_BLOCK, k._1)
				// } else {
					println("refreshAdminBlock into blockUsers" + k._1)
					blockUsers += k._1
				// }
			}
		}

		redis.hgetall(REDIS_KEY_ADMIN_BLOCK_LINE).map { ee =>
			ee.map { k =>
				val unLockTime = k._2.utf8String.split(",")(0).toLong
				// if( now > unLockTime) {
				// 	redis.hdel(REDIS_KEY_ADMIN_BLOCK_LINE, k._1)
				// } else {
					blockLines += k._1
				// }
			}
		}				
	}

	def isBlock(uid : String, lid : String ) = {
		blockUsers.contains(uid) || blockLines.contains(lid)
	}

	def isBlockAction(uid : String, lid : String ) = Action {
		val a = (blockUsers.contains(uid) || blockLines.contains(lid))
		Logger.info(s"adminBlockUser uid:${uid}, lid is ${lid}, isblock ${a}")
		if(a) {
			Status(500)("error")
			} else 


		Ok( a + "" )
	}

	// reasonType 0:all issue, 1:account issue, 2:post issue, 3:message issue
	def adminBlockUser(uid : String, rt : String, lid : String) = Action {
		Logger.info(s"adminBlockUser uid:${uid}, reason type is ${rt}, lid is ${lid}")
		val now = System.currentTimeMillis
		val unlock = now + LOCK_TIME
		redis.hset(REDIS_KEY_ADMIN_BLOCK, uid, unlock + "," + rt)

		// val userData = s"""{"uid":"${uid}", "nn":"${JSONFormat.quoteString(nickname)}", "lid":"${JSONFormat.quoteString(lineId)}", "s":"${s}", "in":"${interests}", "pn":"${places}", "cn":"${careers}", "on":"${olds}", "constel":"${constellations}","mo":"${motions}"} """

		redis.hget(REDIS_KEY_USER, uid).map { user =>
			if(user.isDefined) {
				val jj = mapper.readTree(user.get.utf8String)
				val lidd = jj.get("lid").asText
				if(lidd != "" && lidd != "暫時隱藏")
					redis.hset(REDIS_KEY_ADMIN_BLOCK_LINE, lidd, unlock + "," + rt)				
			}
		}


		
		if(rt == "0" || rt == "1") AccountApplication.delProfile(uid)

		refreshAdminBlock()	
		Ok("")
	}


	val REDIS_KEY_BLOCK_ITEM = "BIT"
	val REDIS_KEY_BLOCK_INDEX = "BIN:"
	def reportBlock(uid : String, toUid: String, desc : String) = Action {
		val now = System.currentTimeMillis
		val bid = java.util.UUID.randomUUID.toString
		val ss = s"""{"bid":"${bid}", "reporter":"${uid}", "block":"${toUid}", "desc":"${desc}", "ts":${now}}"""

		redis.hset(REDIS_KEY_REPORT_BLOCK, bid, ss)
		redis.zadd(REDIS_KEY_BLOCK_INDEX + toUid, (now, bid))

		Ok("ok")
	}

	def getAllBlock( uid :String) = Action.async {
		redis.hgetall(REDIS_KEY_REPORT_BLOCK).map { all => //Future[Map[String, R]]
			val ss = all.map { k => k._2.utf8String }.toArray.mkString("[",",","]")
			Ok(ss)
		}
	}	
}
