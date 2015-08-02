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

object TouchApplication extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
	
	val REDIS_KEY_TOUCH_LINE = "RKTL"
	val REDIS_KEY_TOUCH_CHECK_TIME = "RKTCT"
	val NEXT_TIME = 3600000l
	val DAY_5 = 86400000l * 10
	val DAY_20 = 86400000 * 20	

	def getPlayNextTime(uid :String) = Action.async {
		val now = System.currentTimeMillis

		redis.hget(REDIS_KEY_TOUCH_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss =  tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}


	def createTouch() = Action.async { request =>
		Logger.info(s"createTouch")
		val tid = java.util.UUID.randomUUID.toString
		val baseR = new java.util.Random().nextInt(20)

		val gg = if(baseR == 3) {
			System.currentTimeMillis.toString.takeRight(5) + "0" + 
				(20 + new java.util.Random().nextInt(80))
		} else if(baseR % 5 == 0) {
			System.currentTimeMillis.toString.takeRight(5) + "0" + 
				(100 + new java.util.Random().nextInt(100))			
		} else if(baseR % 5 == 1 || baseR % 5 == 2) {
			System.currentTimeMillis.toString.takeRight(5) + "0" + 
				(200 + new java.util.Random().nextInt(100))			
		} else if(baseR % 5 == 3 || baseR % 5 == 4) {
			System.currentTimeMillis.toString.takeRight(5) + "0" + 
				(300 + new java.util.Random().nextInt(100))			
		}


		// val gg = System.currentTimeMillis.toString.takeRight(5) + "0" + 
		// (50 + new java.util.Random().nextInt(300))

		for {
			a <- redis.hkeys(REDIS_KEY_GIFT_LINE)
			b <- redis.hget(REDIS_KEY_GIFT_LINE, a(new java.util.Random().nextInt(a.size)))
		} yield {
			val kk = mapper.readTree(b.get.utf8String)

			val result = s"""{
				"id":"${tid}",
				"type":"FR_LINE",
				"lgid":"${kk.get("id").asText}",
				"name":"${kk.get("name").asText}",
				"join_count":0,
				"img":"${kk.get("img").asText}",
				"ct":${System.currentTimeMillis},
				"finish":false,
				"tt":"${gg}",
				"winner_id":null,
				"winner_ts":0,
				"winner_msg":null
			}"""		
			redis.hset(REDIS_KEY_TOUCH_LINE, tid, result)

			Ok(result)					
		}
	}

	def list1(uid : String) = Action.async {
		redis.hgetall(REDIS_KEY_TOUCH_LINE).map { s => //Future[Map[String, R]]
			Ok(s.filter { dd =>
				val data = dd._2.utf8String
				val ff = mapper.readTree(data)
				
				if(ff.get("finish").asBoolean && 
					System.currentTimeMillis - ff.get("winner_ts").asLong > DAY_20)
					redis.hdel(REDIS_KEY_TOUCH_LINE, dd._1)

				!(ff.get("finish").asBoolean && 
					System.currentTimeMillis - ff.get("winner_ts").asLong > DAY_5)
			}.map { dd =>
				val data = dd._2.utf8String
				val ff = mapper.readTree(data)

				(data, ff.get("finish").asBoolean)
			}.toList.sortBy(_._2).map( _._1 ).mkString("[", ",", "]"))
		}
	}


	def getRunningList(uid : String) = Action.async {
		redis.hgetall(REDIS_KEY_TOUCH_LINE).map { s => //Future[Map[String, R]]
			Ok(s.filter { dd =>
				val data = dd._2.utf8String
				val ff = mapper.readTree(data)
				
				if(ff.get("finish").asBoolean && 
					System.currentTimeMillis - ff.get("winner_ts").asLong > DAY_20)
					redis.hdel(REDIS_KEY_TOUCH_LINE, dd._1)

				!ff.get("finish").asBoolean
			}.map { dd =>
				val data = dd._2.utf8String
				val ff = mapper.readTree(data)

				(data, ff.get("ct").asLong)
			}.toList.sortBy(_._2).reverse.map( _._1 ).mkString("[", ",", "]"))
		}
	}


	def getWinnerList(uid : String) = Action.async {
		redis.hgetall(REDIS_KEY_TOUCH_LINE).map { s => //Future[Map[String, R]]
			Ok(s.filter { dd =>
				val data = dd._2.utf8String
				val ff = mapper.readTree(data)
				
				if(ff.get("finish").asBoolean && 
					System.currentTimeMillis - ff.get("winner_ts").asLong > DAY_20)
					redis.hdel(REDIS_KEY_TOUCH_LINE, dd._1)

				ff.get("finish").asBoolean
			}.map { dd =>
				val data = dd._2.utf8String
				val ff = mapper.readTree(data)

				(data, ff.get("winner_ts").asLong)
			}.toList.sortBy(_._2).reverse.map( _._1 ).take(30).mkString("[", ",", "]"))
		}
	}	

	def updateMsg(uid : String, tid : String, msg : String) = Action.async {
		for {
			s <- redis.hget(REDIS_KEY_TOUCH_LINE, tid) //Future[Option[R]]
		} yield {
			if(s.isDefined) {
				val data = s.get.utf8String
				val ff = mapper.readTree(data)
				val now = System.currentTimeMillis

				if(ff.get("finish").asBoolean) {
					val jjCount = ff.get("join_count").asInt
					val ccount = if(jjCount < 10) "00" + jjCount 
								else if(jjCount < 100) "0"+ jjCount 
								else jjCount.toString
					val magic = ff.get("tt").asText.takeRight(3)
					val lgid = ff.get("lgid").asText

					if(ccount != magic) {
						Ok("err:3")
					} else if(uid != ff.get("winner_id").asText) {
						Ok("err:4")
					} else {
						val result = s"""{
							"id":"${ff.get("id").asText}",
							"type":"${ff.get("type").asText}",
							"lgid":"${ff.get("lgid").asText}",
							"name":"${ff.get("name").asText}",
							"join_count":${ff.get("join_count").asInt},
							"img":"${ff.get("img").asText}",
							"ct":${ff.get("ct").asLong},
							"tt":"${ff.get("tt").asText}",
							"finish":${ff.get("finish").asBoolean},
							"winner_id":"${ff.get("winner_id").asText}",
							"winner_ts":${ff.get("winner_ts").asLong},
							"winner_msg":"${msg}"
						}"""							
						redis.hset(REDIS_KEY_TOUCH_LINE, ff.get("id").asText, result)
						Ok("")					
					}
				} else Ok("err:2")
			} else {
				Ok("err:1")
			} 						
		}

	}


	def play1(uid : String, tid : String, quick : Boolean) = Action.async {
		for {
			s <- redis.hget(REDIS_KEY_TOUCH_LINE, tid) //Future[Option[R]]
			tt <- redis.hget(REDIS_KEY_TOUCH_CHECK_TIME, uid)
			b <- redis.hget(PointApplication.REDIS_KEY_POINT, uid) // uid point
		} yield {
			val now = System.currentTimeMillis
			
			val userPoint =if(b.isDefined) {
				 b.get.utf8String.toLong
			} else {
				0l
			}

			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				val sss = tt.get.utf8String.toLong - now
				Ok("err:5:" + sss)
			} else if(quick && userPoint < 50) {
				Ok("err:6")
			} else if(s.isDefined) {
				val data = s.get.utf8String
				val ff = mapper.readTree(data)
				val now = System.currentTimeMillis

				if(!ff.get("finish").asBoolean) {
					val jjCount = ff.get("join_count").asInt + 1
					val ccount = if(jjCount < 10) "00" + jjCount 
								else if(jjCount < 100) "0"+ jjCount 
								else jjCount.toString
					val magic = ff.get("tt").asText.takeRight(3)
					val lgid = ff.get("lgid").asText

					if(ccount == magic) {
						redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
						val code = GiftApplication.getCode(lgid, uid)
						redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)					
						redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
						redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)

						val result = s"""{
							"id":"${ff.get("id").asText}",
							"type":"${ff.get("type").asText}",
							"lgid":"${ff.get("lgid").asText}",
							"name":"${ff.get("name").asText}",
							"join_count":${jjCount},
							"img":"${ff.get("img").asText}",
							"ct":${ff.get("ct").asLong},
							"finish":true,
							"tt":"${ff.get("tt").asText}",							
							"winner_id":"${uid}",
							"winner_ts":${System.currentTimeMillis},
							"winner_msg":null
						}"""							
						redis.hset(REDIS_KEY_TOUCH_LINE, ff.get("id").asText, result)
						
						if(quick) {
							redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
						}	

						Ok("lucky")
					} else {
						val result = s"""{
							"id":"${ff.get("id").asText}",
							"type":"${ff.get("type").asText}",
							"lgid":"${ff.get("lgid").asText}",
							"name":"${ff.get("name").asText}",
							"join_count":${jjCount},
							"img":"${ff.get("img").asText}",
							"ct":${ff.get("ct").asLong},
							"finish":false,
							"tt":"${ff.get("tt").asText}",							
							"winner_id":null,
							"winner_ts":0,
							"winner_msg":""
						}"""							
						redis.hset(REDIS_KEY_TOUCH_LINE, ff.get("id").asText, result)
						val nextPlay = now + NEXT_TIME + new java.util.Random().nextInt(1800000)
						redis.hset(REDIS_KEY_TOUCH_CHECK_TIME, uid, nextPlay)
						
						if(quick) {
							redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
						}	

						Ok("no_lucky")
					}
				} else Ok("err:2")
			} else {
				Ok("err:1")
			} 
		}
	}
}