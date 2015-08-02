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
import controllers.line.TraceApplication._
import java.util.Random
import scala.concurrent.duration._



object HappyTimeApplication extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val NEXT_TIME = 3600000 * 2l
	// val NEXT_TIME = 2l

	val REDIS_KEY_HAPPY_TIME = "RKHTIG"
	val REDIS_KEY_HAPPY_TIME_HISTORY = "RKHTGHIS"
	val REDIS_KEY_HAPPY_TIME_NOW = "RKHTGNOW"
	val REDIS_KEY_HAPPY_TIME_MAX_VALUE = "RKHTGMV"
	val REDIS_KEY_HAPPY_TIME_FINNAL_UID = "RKHTGFU"
	val REDIS_KEY_HAPPY_TIME_CHECK_TIME = "RKHTGCT"
	val REDIS_KEY_HAPPY_TIME_NOW_FINISH = "RKHTGNF"
	val REDIS_KEY_HAPPY_TIME_WINNER = "RKHTGWINER"
	val HAPPY_TIME_DAY = 1.5
	val HAPPY_TIME_BASE_COUNT = 0
	val HAPPY_PERIOD = 300000

	def getPlayHappyTimeNextTime(uid :String, happyTime : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_HAPPY_TIME_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current happyTime:${happyTime}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss =  tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}


	def createHappyTime(uid : String) = Action.async { request =>
		Logger.info(s"create happyTime uid : " + uid)

		if(uid == "29FF1053B1E30818A1131500269B50549B5C7285") {
			createFinalHappyTime().map { rr =>
				Ok(rr)
			}
		} else Future.successful(Ok("error"))
	}

	def createFinalHappyTime() = {
		for {
			a <- redis.hkeys(REDIS_KEY_GIFT_LINE)
			b <- redis.hget(REDIS_KEY_GIFT_LINE, a(new java.util.Random().nextInt(a.size)))
			c <- redis.get(REDIS_KEY_HAPPY_TIME_NOW_FINISH)
		} yield {
			val happyTime = java.util.UUID.randomUUID.toString
			val now = System.currentTimeMillis
			val end = 0
			val happyt = new java.util.Random().nextInt(86400000 - HAPPY_PERIOD)
						
			if(!c.isDefined || (c.isDefined && c.get.utf8String == "Y")) {
				val kk = mapper.readTree(b.get.utf8String)
				val result = s"""{
					"id":"${happyTime}",
					"type":"FR_LINE",
					"lgid":"${kk.get("id").asText}",
					"name":"${kk.get("name").asText}",
					"base_count":0,
					"img":"${kk.get("img").asText}",
					"end":0,
					"start":${now},
					"winner_id":null,
					"winner_ts":0,
					"winner_msg":null
				}"""		
				redis.hset(REDIS_KEY_HAPPY_TIME, happyTime, result)	
				redis.set(REDIS_KEY_HAPPY_TIME_NOW, happyTime)
				redis.set(REDIS_KEY_HAPPY_TIME_MAX_VALUE, happyt.toString)	
				redis.set(REDIS_KEY_HAPPY_TIME_NOW_FINISH, "N")
				result	
			} else "error"				
		}
	}


	def getHappyTimeHistory(uid : String, happyTime : String) = Action.async {
		Logger.info(s"getHAPPY_TIMEHistory uid:${uid}, HAPPY_TIME:${happyTime}")

		redis.hgetall(REDIS_KEY_HAPPY_TIME_HISTORY + happyTime).map { e => //Future[Map[String, R]]
			val ee = e.map { k => 
				val dd = k._2.utf8String.split("##")(1).toFloat
				(dd, s"""{"ts":${k._1},"h":"${k._2.utf8String}"}""")
			}.toArray.sortBy(_._1)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)
		}
	}

	def getWinner(uid : String) = Action.async {
		for {
			a <- redis.hgetall(REDIS_KEY_HAPPY_TIME_WINNER) //Future[Map[String, R]]
			b <- if(a.size > 0) redis.hmget(REDIS_KEY_HAPPY_TIME, a.keys.toList : _*) else Future.successful(List())	// Future[Seq[Option[R]]]		
		} yield {
			Ok(b.flatten.map { s =>
					val ss = s.utf8String 
					val ff = mapper.readTree(ss)
					(ff.get("winner_ts").asLong, ss)
				}.sortBy( _._1 ).reverse.map { _._2 }.mkString("[", ",", "]"))
		}
	}


	def getCurrentHappyTime(uid : String) = Action.async {
		for {
			a <- redis.get(REDIS_KEY_HAPPY_TIME_NOW)
			b <- redis.hget(REDIS_KEY_HAPPY_TIME, a.get.utf8String)
			c <- redis.hgetall(REDIS_KEY_HAPPY_TIME_HISTORY + a.get.utf8String)
		} yield {
			val ee = c.map { k => 
				val dd = k._1.toFloat
				(dd, s"""{"ts":${k._1},"h":"${JSONFormat.quoteString(k._2.utf8String)}"}""")}.toArray.sortBy(_._1)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(s"""{"data":${b.get.utf8String},"history":${ooo}}""")
		}
	}

	def updateMsg(uid : String, happyTime : String, msg : String) = Action.async {
		for {
			s <- redis.hget(REDIS_KEY_HAPPY_TIME, happyTime) //Future[Option[R]]
			a <- redis.hget(REDIS_KEY_HAPPY_TIME_WINNER, happyTime)
		} yield {
			if(s.isDefined) {
				val data = s.get.utf8String
				val ff = mapper.readTree(data)
				val now = System.currentTimeMillis

				if(a.isDefined) {
					val lgid = ff.get("lgid").asText
					if(uid != ff.get("winner_id").asText) {
						Ok("err:4")
					} else {
						val result = s"""{
							"id":"${ff.get("id").asText}",
							"type":"${ff.get("type").asText}",
							"lgid":"${ff.get("lgid").asText}",
							"name":"${ff.get("name").asText}",
							"base_count":0,
							"img":"${ff.get("img").asText}",
							"end":${ff.get("end").asText},
							"start":${ff.get("start").asText},
							"winner_id":"${ff.get("winner_id").asText}",
							"winner_ts":${ff.get("winner_ts").asLong},
							"winner_msg":"${JSONFormat.quoteString(msg)}"
						}"""	

						redis.hset(REDIS_KEY_HAPPY_TIME, ff.get("id").asText, result)
						Ok("")					
					}
				} else Ok("err:2")
			} else {
				Ok("err:1")
			} 						
		}
	}


	import java.util.Calendar
	import java.util.TimeZone

	// def createWinner() {
	// 	val now = System.currentTimeMillis
	// 	val uid = "88495E0C78F71FF27F69597B74DDEDC388CED205"
	// 	val happyTime = "02"

	// 	for {
	// 		c <- redis.get(REDIS_KEY_HAPPY_TIME_MAX_VALUE) // is happyTimeger than max
	// 		d <- redis.hget(REDIS_KEY_HAPPY_TIME, happyTime) // is end 
	// 	} yield {
	// 		try {
	// 			val ff = mapper.readTree(d.get.utf8String)
	// 			val lgid = ff.get("lgid").asText

	// 			cal.set(Calendar.HOUR_OF_DAY, 0)
	// 			cal.set(Calendar.MINUTE, 0)
	// 			cal.set(Calendar.SECOND, 0)

	// 			val hstart = cal.getTimeInMillis + c.get.utf8String.toLong
	// 			val hend = hstart + HAPPY_PERIOD

	// 			redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
	// 			val code = GiftApplication.getCode(lgid, uid)
	// 			redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)					
	// 			redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
	// 			redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
	// 			redis.set(REDIS_KEY_HAPPY_TIME_NOW_FINISH, "Y")

	// 			val result = s"""{
	// 				"id":"${ff.get("id").asText}",
	// 				"type":"${ff.get("type").asText}",
	// 				"lgid":"${ff.get("lgid").asText}",
	// 				"name":"${ff.get("name").asText}",
	// 				"base_count":${ff.get("base_count").asText},
	// 				"img":"${ff.get("img").asText}",
	// 				"end":${hend},
	// 				"start":${hstart},
	// 				"winner_id":"${uid}",
	// 				"winner_ts":${now},
	// 				"winner_msg":""
	// 			}"""	
	// 			println("result:" + result)
	// 			redis.hset(REDIS_KEY_HAPPY_TIME, happyTime, result)
	// 			redis.hset(REDIS_KEY_HAPPY_TIME_WINNER, happyTime, uid)	
	// 		} catch {
	// 			case e : Throwable => e.printStackTrace
	// 		}		
	// 	}
	// }

	// def createTmpHappyTime() = {
	// 	for {
	// 		b <- redis.hget(REDIS_KEY_GIFT_LINE, "cbe81814-1523-4e2e-b530-a6fd1b42fac6")
	// 	} yield {
	// 		val happyTime = "02"
	// 		val now = System.currentTimeMillis
						
	// 		val kk = mapper.readTree(b.get.utf8String)
	// 		val result = s"""{
	// 			"id":"${happyTime}",
	// 			"type":"FR_LINE",
	// 			"lgid":"${kk.get("id").asText}",
	// 			"name":"${kk.get("name").asText}",
	// 			"base_count":0,
	// 			"img":"${kk.get("img").asText}",
	// 			"end":0,
	// 			"start":${now},
	// 			"winner_id":null,
	// 			"winner_ts":0,
	// 			"winner_msg":null
	// 		}"""		
	// 		redis.hset(REDIS_KEY_HAPPY_TIME, happyTime, result)
	// 	}
	// }


	def playNewHappyTime(happyTime : String, uid : String, nickName : String, quick : Boolean) = Action.async {
		val now = {
			System.currentTimeMillis.toString.dropRight(3) + (100 + new java.util.Random().nextInt(899)).toString
		}.toLong

		for {
			a <- redis.get(REDIS_KEY_HAPPY_TIME_NOW) // is running
			c <- redis.get(REDIS_KEY_HAPPY_TIME_MAX_VALUE) // is happyTimeger than max
			d <- redis.hget(REDIS_KEY_HAPPY_TIME, happyTime) // is end 
			tt <- redis.hget(REDIS_KEY_HAPPY_TIME_CHECK_TIME, uid)
			b <- redis.hget(PointApplication.REDIS_KEY_POINT, uid) // uid point
			f <- redis.get(REDIS_KEY_HAPPY_TIME_NOW_FINISH)
		} yield {
			val userPoint =if(b.isDefined) {
				 b.get.utf8String.toLong
			} else {
				0l
			}

			if(f.isDefined && f.get.utf8String == "Y") {
				Ok("err:3")
			} else if(tt.isDefined && now < tt.get.utf8String.toLong) {
				val sss = tt.get.utf8String.toLong - now
				Ok("err:5:" + sss)
			} else if(quick && userPoint < 50) {
				Ok("err:2")
			} else if(!a.isDefined || a.get.utf8String != happyTime) {
				Ok("err:1")
			} else if(!c.isDefined) {
				Ok("err:3")
			} else if(!d.isDefined) {
				Ok("err:3")
			} else {
				val cal = Calendar.getInstance()
				cal.set(Calendar.HOUR_OF_DAY, 0)
				cal.set(Calendar.MINUTE, 0)
				cal.set(Calendar.SECOND, 0)

				Logger.info(s"check time origin ${cal.getTimeInMillis} ${c.get.utf8String.toLong}")

				val hstart = cal.getTimeInMillis + c.get.utf8String.toLong
				val hend = hstart + HAPPY_PERIOD
				val nmessage = if(nickName == null || (nickName != null && nickName.trim == "")) "." else nickName

				for {
					aa <- redis.hset(REDIS_KEY_HAPPY_TIME_HISTORY + happyTime, now.toString , uid + "##" + nmessage)
					aa1 <- redis.hset(REDIS_KEY_HAPPY_TIME_CHECK_TIME, uid, now + NEXT_TIME)	
					aa2 <- if(quick) redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
						else Future.successful(true)
				} {}

				// println(s"check time $now $hstart $hend")
				Logger.info(s"check time  $now $hstart $hend")

				if(now >= hstart && now <= hend) {
					val ff = mapper.readTree(d.get.utf8String)
					val lgid = ff.get("lgid").asText

					redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
					val code = GiftApplication.getCode(lgid, uid)
					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)					
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
					redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
					redis.set(REDIS_KEY_HAPPY_TIME_NOW_FINISH, "Y")

					val result = s"""{
						"id":"${ff.get("id").asText}",
						"type":"${ff.get("type").asText}",
						"lgid":"${ff.get("lgid").asText}",
						"name":"${ff.get("name").asText}",
						"base_count":${ff.get("base_count").asText},
						"img":"${ff.get("img").asText}",
						"end":${hend},
						"start":${hstart},
						"winner_id":"${uid}",
						"winner_ts":${now},
						"winner_msg":null
					}"""	

					redis.hset(REDIS_KEY_HAPPY_TIME, happyTime, result)
					redis.hset(REDIS_KEY_HAPPY_TIME_WINNER, happyTime, uid)

					Akka.system.scheduler.scheduleOnce(300 second) {
						createFinalHappyTime()
					}

					Ok("lucky")
				} else {
					Ok("no_lucky")
				}
			}
		}
	}	
}
