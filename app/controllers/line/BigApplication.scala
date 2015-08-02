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


object BigApplication extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val NEXT_TIME = 3600000 * 2l
	// val NEXT_TIME = 2l

	val REDIS_KEY_BIG = "RKBIG"
	val REDIS_KEY_BIG_HISTORY = "RKBGHIS"
	val REDIS_KEY_BIG_NOW = "RKBGNOW"
	val REDIS_KEY_BIG_MAX_VALUE = "RKBGMV"
	val REDIS_KEY_BIG_FINNAL_UID = "RKBGFU"
	val REDIS_KEY_BIG_CHECK_TIME = "RKBGCT"
	val REDIS_KEY_BIG_NOW_FINISH = "RKBGNF"
	val REDIS_KEY_BIG_WINNER = "RKBGWINER"
	val BIG_DAY = 1.5
	val BIG_BASE_COUNT = 0

	def getPlayBigNextTime(uid :String, BIG : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_BIG_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current BIG:${BIG}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss =  tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}


	def createBig(uid : String) = Action.async { request =>
		Logger.info(s"create big uid : " + uid)

		if(uid == "29FF1053B1E30818A1131500269B50549B5C7285") {
			createFinalBig().map { s =>
				Ok(s)
			}
		} else Future.successful(Ok("error"))
	}

	def createFinalBig() = {
		for {
			a <- redis.hkeys(REDIS_KEY_GIFT_LINE)
			b <- redis.hget(REDIS_KEY_GIFT_LINE, a(new java.util.Random().nextInt(a.size)))
			c <- redis.get(REDIS_KEY_BIG_NOW_FINISH)
		} yield {
			if(c.isDefined && c.get.utf8String == "Y") {
				val big = java.util.UUID.randomUUID.toString
				val now = System.currentTimeMillis
				val end = now + (BIG_DAY * 86400000)

				val kk = mapper.readTree(b.get.utf8String)
				val result = s"""{
					"id":"${big}",
					"type":"FR_LINE",
					"lgid":"${kk.get("id").asText}",
					"name":"${kk.get("name").asText}",
					"base_count":${BIG_BASE_COUNT},
					"img":"${kk.get("img").asText}",
					"end":${end},
					"start":${now}
				}"""		
				redis.hset(REDIS_KEY_BIG, big, result)	
				redis.set(REDIS_KEY_BIG_NOW, big)
				redis.set(REDIS_KEY_BIG_MAX_VALUE, BIG_BASE_COUNT.toString)	
				redis.set(REDIS_KEY_BIG_NOW_FINISH, "N")
				result		
			} else "error"		
		}		
	}



	def getBigHistory(uid : String, big : String) = Action.async {
		Logger.info(s"getBIGHistory uid:${uid}, BIG:${big}")

		redis.hgetall(REDIS_KEY_BIG_HISTORY + big).map { e => //Future[Map[String, R]]
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
			a <- redis.hgetall(REDIS_KEY_BIG_WINNER) //Future[Map[String, R]]
			b <- redis.hmget(REDIS_KEY_BIG, a.keys.toList : _*)	// Future[Seq[Option[R]]]		
		} yield {
			Ok(b.flatten.map { _.utf8String }.mkString("[", ",", "]"))
		}
	}


	def getCurrentBig(uid : String) = Action.async {
		for {
			a <- redis.get(REDIS_KEY_BIG_NOW)
			b <- redis.hget(REDIS_KEY_BIG, a.get.utf8String)
			c <- redis.hgetall(REDIS_KEY_BIG_HISTORY + a.get.utf8String)
			d <- redis.get(REDIS_KEY_BIG_MAX_VALUE)
			e <- redis.get(REDIS_KEY_BIG_NOW_FINISH)
		} yield {
			val now = System.currentTimeMillis
			val endTime = {
				val ff = mapper.readTree(b.get.utf8String)
				ff.get("end").asLong
			}

			if(now >= endTime && e.isDefined && e.get.utf8String == "N") {
				doF(a.get.utf8String)
			}

			val ee = c.map { k => 
				val dd = k._2.utf8String.split("##")(1).toFloat
				(dd, s"""{"ts":${k._1},"h":"${JSONFormat.quoteString(k._2.utf8String)}"}""")}.toArray.sortBy(_._1)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(s"""{"data":${b.get.utf8String},"history":${ooo},"now_base":${d.get.utf8String}, "final":${now >= endTime}}""")
		}
	}

	def doF(big : String) = {
		val now = System.currentTimeMillis

		for {
			a <- redis.hget(REDIS_KEY_BIG_FINNAL_UID, big)
			c <- redis.hget(REDIS_KEY_BIG, big)
			d <- redis.get(REDIS_KEY_BIG_MAX_VALUE)
		} yield {
			if(a.isDefined && d.isDefined) {
				val ff = mapper.readTree(c.get.utf8String)
				val lgid = ff.get("lgid").asText
				val uid = a.get.utf8String

				redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
				val code = GiftApplication.getCode(lgid, uid)
				redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)					
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
				redis.set(REDIS_KEY_BIG_NOW_FINISH, "Y")
				redis.hset(REDIS_KEY_BIG_WINNER, big, uid)


				Akka.system.scheduler.scheduleOnce(300 second) {
					createFinalBig()
				}
			}
		}		
	}


			
	def playNewBig(big : String, uid : String, nickName : String, quick : Boolean) = Action.async {
		val now = {
			System.currentTimeMillis.toString.dropRight(3) + (100 + new java.util.Random().nextInt(899)).toString
		}.toLong
		
		val s = new java.util.Random().nextInt.abs.toString.take(5)
		val s1 = new java.util.Random().nextInt(10).toString
		val bigValue = (s1 + "." + s).toDouble

		for {
			a <- redis.get(REDIS_KEY_BIG_NOW) // is running
			c <- redis.get(REDIS_KEY_BIG_MAX_VALUE) // is bigger than max
			d <- redis.hget(REDIS_KEY_BIG, big) // is end 
			tt <- redis.hget(REDIS_KEY_BIG_CHECK_TIME, uid)
			b <- redis.hget(PointApplication.REDIS_KEY_POINT, uid) // uid point
		} yield {
			val userPoint =if(b.isDefined) {
				 b.get.utf8String.toLong
			} else {
				0l
			}

			val endTime = {
				val ff = mapper.readTree(d.get.utf8String)
				ff.get("end").asLong
			}

			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				val sss = tt.get.utf8String.toLong -now
				Ok("err:5:" + sss)
			} else if(quick && userPoint < 50) {
				Ok("err:2")
			} else if(!a.isDefined || a.get.utf8String != big) {
				Ok("err:1")
			} else if(endTime <= now) {
				Ok("err:4")
			} else if(!c.isDefined) {
				Ok("err:3")
			} else if(c.get.utf8String.toDouble > bigValue) {
				redis.hset(REDIS_KEY_BIG_HISTORY + big, now.toString , uid + "##" + bigValue + "##" + nickName)
				val nextPlay = now + NEXT_TIME

				redis.hset(REDIS_KEY_BIG_CHECK_TIME, uid, nextPlay)
				if(quick) {
					redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
				}

				Ok(bigValue  + "")
			}  else {
				redis.hset(REDIS_KEY_BIG_HISTORY + big, now.toString , uid + "##" + bigValue + "##" + nickName)
				redis.set(REDIS_KEY_BIG_MAX_VALUE, bigValue.toString)
				redis.hset(REDIS_KEY_BIG_FINNAL_UID, big, uid)
				val nextPlay = now + NEXT_TIME
				redis.hset(REDIS_KEY_BIG_CHECK_TIME, uid, nextPlay)	
				
				if(quick) {
					redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
				}

				Ok(bigValue + ":highest")
			}
		}
	}	
}
