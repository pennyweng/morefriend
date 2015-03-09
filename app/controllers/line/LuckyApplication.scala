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

object LuckyApplication extends Controller {
	implicit val system = Akka.system

	val REDIS_KEY_LUCKY_RUNNING = "LR" 
	val REDIS_KEY_LUCKY_FINISH = "LF" 
	val REDIS_KEY_LUCKY_USER_NUMBER = "LUN:"
	val REDIS_KEY_LUCKY_HISTORY = "LH:"
	val REDIS_KEY_LUCKY_HISTORY_M = "LHM:"
	val REDIS_KEY_LUCKY_CHECK_TIME = "LCT"
	val REDIS_KEY_LUCKY_WINNER_HISTORY = "LWH"

	val NEXT_TIME = 3600000 * 6l
	val MAX_COUNT = 3
	val mapper = new ObjectMapper()
	val redis = RedisClient(REDIS_HOST, 6379)	

	def getNextTime(uid :String, lkid : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_LUCKY_CHECK_TIME + lkid, uid).map { tt =>
			if(tt.isDefined && now - tt.get.utf8String.toLong < NEXT_TIME) {
				Logger.info(s"time is not over,current lkid:${lkid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = NEXT_TIME - (now - tt.get.utf8String.toLong)
				Ok(sss + "")
			} else Ok("0")
		}
	}

	def createLucky(lkid : String) = Action { request =>
		Logger.info(s"createLucky lkid ${lkid}")
		val body = request.body
		val data = body.asText.getOrElse("")
		redis.hset(REDIS_KEY_LUCKY_RUNNING, lkid, data)

		Ok(data)		
	}


	def getAllLucky(uid : String) = Action {
		Logger.info(s"getAllLucky uid ${uid}")
		Ok("[]")
	}

	def getAllNewLucky(uid : String) = Action.async {
		Logger.info(s"getAllLucky uid ${uid}")

		redis.hgetall(REDIS_KEY_LUCKY_RUNNING).map {  ee =>//Future[Map[String, R]]
			val ee1 = ee.map { k => (new java.util.Random().nextInt(100), s"${k._2.utf8String}") }.toArray
			scala.util.Sorting.quickSort(ee1)
			Ok(ee1.map { k => k._2 }.take(MAX_COUNT).mkString("[",",", "]"))
		}
	}


	def putWinnerLucky(uid : String, lkid: String, winCount : Long, desc : String) = Action.async {
		Logger.info(s"putWinnerLucky uid ${uid}, lkid ${lkid} winCount ${winCount}")
		val now = System.currentTimeMillis
		for {
			dd <- redis.hget(REDIS_KEY_LUCKY_RUNNING, lkid)	
			ss <- redis.zrangebyscore(REDIS_KEY_LUCKY_HISTORY + lkid, 
				Limit(winCount), 
				Limit(winCount)) //Future[Seq[R]]
		} yield {
			val uud = if(ss.toList.size > 0) {
				val jj = mapper.readTree(ss(0).utf8String)
				(jj.get("uid").asText, jj.get("lid").asText)
			} else ("", "")
			if(dd.isDefined && uud._1 != "") {
				val luckyData = mapper.readTree(dd.get.utf8String)
				val rest = s"""{"uid":"${uud._1}",
				"award":"${luckyData.get("name").asText}", 
				"nn":"${winCount}", 
				"desc":"${desc}",
				"lid":"${uud._2}",
				"total":${luckyData.get("current_count").asText},
				"lkid":"${lkid}",
				"ts":${now}}"""
				for {
					a <- redis.hset(REDIS_KEY_LUCKY_WINNER_HISTORY, lkid, rest)
					b <- redis.hset(REDIS_KEY_LUCKY_FINISH, lkid, dd.get.utf8String)
					c <- redis.hdel(REDIS_KEY_LUCKY_RUNNING, lkid) 
				} ()
				Ok("")
			} else {
				Logger.info(s"cannnot find the history and luck runnging")
				Ok("")
			}
		}
	}

	def getAllWinner(uid : String) = Action.async {
		Logger.info(s"getAllWinner uid ${uid}")
		redis.hgetall(REDIS_KEY_LUCKY_WINNER_HISTORY).map {  ee =>//Future[Map[String, R]]
			Ok(ee.map { k =>
				s"${k._2.utf8String}"
			}.mkString("[",",", "]"))
		}
	}	

	def playGame(uid : String, lkid : String, desc : String, lid : String) = Action.async {
		Logger.info(s"play uid ${uid}, lkid ${lkid}, desc ${desc}")
		val now = System.currentTimeMillis

		val s = for {
			tt <- redis.hget(REDIS_KEY_LUCKY_CHECK_TIME + lkid, uid)
			dd <- redis.hget(REDIS_KEY_LUCKY_RUNNING, lkid)
		} yield {
			if(tt.isDefined && now - tt.get.utf8String.toLong < NEXT_TIME) {
				Logger.info(s"time is not over,current lkid:${lkid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = NEXT_TIME - (now - tt.get.utf8String.toLong)
				"err:3:" + sss
			} else if(!dd.isDefined) { 
				"err:1"
			} else  {
				val jj = mapper.readTree(dd.get.utf8String)
				val maxCount = jj.get("max_count").asLong
				val currentCount = jj.get("current_count").asLong
				if(currentCount >= maxCount) {
					"err:2"
				} else {
					dd.get.utf8String
				}
			}
		}

		for {
			v <- s
			nn <- if(v.indexOf("err") != -1) Future.successful(-1l) else redis.incr(REDIS_KEY_LUCKY_USER_NUMBER + lkid)
		} yield {
			if(nn == -1) Ok(v)
			else {
				val jj = mapper.readTree(v)
				val ns = s"""{"id": "${jj.get("id").asText}",
					"name": "${jj.get("name").asText}",
					"image": "${jj.get("image").asText}",
				    "desc": "${jj.get("desc").asText}",
				    "award": "${jj.get("award").asText}",
				    "max_count": ${jj.get("max_count").asLong},
				    "current_count":${nn}
				}"""				
				saveLuckyData(uid, lkid, nn, desc, ns, lid)
				Ok(nn + "")
			}
		}
	}

	def getYourHistory(uid : String, lkid: String) = Action.async {
		Logger.info(s"getYourHistory uid ${uid} lkid ${lkid}")
		redis.hgetall(REDIS_KEY_LUCKY_HISTORY_M + lkid + uid).map {  ee =>//Future[Map[String, R]]
			Ok(ee.map { k =>
				s"""${k._2.utf8String}"""
			}.mkString("[",",", "]"))
		}		
	}

	def getAllHistory(uid : String, lkid: String) = Action.async {
		for {
			ut <- redis.zrevrange(REDIS_KEY_LUCKY_HISTORY + lkid, 0, 1000) // //Future[Seq[R]]
		} yield {
			val resp = ut.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")
			Ok(resp)
		}			
	}


	def saveLuckyData(uid : String, lkid : String, nn : Long, desc : String, ns :String, lid: String) {
		val now = System.currentTimeMillis
		val aa = s"""{"uid":"${uid}", "nn":${nn}, "desc":"${desc}", "ts":${now}, "lid":"${lid}"}"""
		for {
			a <- redis.zadd(REDIS_KEY_LUCKY_HISTORY + lkid, (nn, aa))
			b <- redis.hset(REDIS_KEY_LUCKY_HISTORY_M + lkid + uid, nn + "", aa)
			c <- redis.hset(REDIS_KEY_LUCKY_RUNNING, lkid, ns)
			t <- redis.hset(REDIS_KEY_LUCKY_CHECK_TIME + lkid, uid, now)
		} yield{
			""
		}
	}

// [
//     {
//         "pkg": "com.htc.app1"
//     },
//     {
//         "pkg": "com.htc.app2"
//     },
//     {
//         "pkg": "com.htc.app3"
//     }
// ]
	// def getMFU() = Action { request =>
	// 	Logger.info(s"getMFU ")
	// 	try {
	// 		val data = request.body.asText.getOrElse("[]")
	// 		val jj = mapper.readTree(data)
	// 		val ss = jj.elements.toArray.map { kk =>
	// 			(new Random().nextInt(1000), kk)
	// 		}.toArray
	// 		scala.util.Sorting.quickSort(ss)

	// 		val appResult = 
	// 			modes.map { mode =>
	// 				val dd = (mode - 1) * 8
	// 				s"""{"mode":${mode}, "apps":${vv.drop(dd).take(8).map{ _._2.toString }.mkString("[",",","]")}}"""
	// 			}.mkString("[",",","]")

	// 		Ok(s"""{"res":${appResult}}""")	
	// 	} catch {
	// 		case e : Throwable => println(e.printStackTrace)
	// 		Ok(s"""{"rec":[]}""")
	// 	}	
	// }	
}