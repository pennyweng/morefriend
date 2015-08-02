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
import akka.util.ByteString
import scala.concurrent.duration._


object GLuckyApplication extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
	
	val REDIS_KEY_GLUCKY_NOW = "RKGLN"
	val REDIS_KEY_GLUCKY = "RKGL"
	val REDIS_KEY_GLUCKY_ANS = "RKGLA"
	val REDIS_KEY_GLUCKY_CHECK_TIME = "RKGLCT"
	val REDIS_KEY_GLUCKY_HISTORY = "RKGLHS"
	val REDIS_KEY_GLUCKY_NOW_FINISH = "RKGLNF"

	val NEXT_TIME = 3600000l
	val DAY_5 = 86400000l * 10
	val DAY_20 = 86400000 * 20	
	val BASE = 800

	def getPlayNextTime(uid :String) = Action.async {
		val now = System.currentTimeMillis

		redis.hget(REDIS_KEY_GLUCKY_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss =  tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}


	def createGLucky(uid : String) = Action.async { request =>
		Logger.info(s"createGLucky")

		if(uid == "29FF1053B1E30818A1131500269B50549B5C7285") {
			createFinalGlucky().map { s =>
				Ok(s)
			}
		} else {
			Logger.info("forbid to create final number uid :" + uid)
			Future.successful(Ok("error"))
		}
	}

	def createFinalGlucky() = {
		for {
			a <- redis.hkeys(REDIS_KEY_GIFT_LINE)
			b <- redis.hget(REDIS_KEY_GIFT_LINE, a(new java.util.Random().nextInt(a.size)))
			c <- redis.get(REDIS_KEY_GLUCKY_NOW_FINISH)
		} yield {
			if(c.isDefined && c.get.utf8String == "Y") {
				val tid = java.util.UUID.randomUUID.toString
				val gg = new java.util.Random().nextInt(BASE)
				val kk = mapper.readTree(b.get.utf8String)

				val result = s"""{
					"id":"${tid}",
					"type":"FR_LINE",
					"lgid":"${kk.get("id").asText}",
					"name":"${kk.get("name").asText}",
					"join_count":0,
					"img":"${kk.get("img").asText}",
					"ct":${System.currentTimeMillis},
					"big":$BASE,
					"small":0,
					"finish":false,
					"winner_id":null,
					"winner_ts":0,
					"winner_msg":null
				}"""		
				redis.hset(REDIS_KEY_GLUCKY, tid, result)
				redis.hset(REDIS_KEY_GLUCKY_ANS, tid, gg.toString)
				redis.set(REDIS_KEY_GLUCKY_NOW, tid)
				redis.set(REDIS_KEY_GLUCKY_NOW_FINISH, "N")
				result			
			} else "error"
		}		
	}


	def getRunningList(uid : String) = Action.async {
		for {
			a <- redis.get(REDIS_KEY_GLUCKY_NOW)
			b <- if(a.isDefined) redis.hget(REDIS_KEY_GLUCKY, a.get.utf8String)
				else Future.successful(None)
		} yield {
			Ok(b.getOrElse(ByteString.empty).utf8String)
		}
	}

	def getRunningList1(uid : String) = Action.async {
		for {
			a <- redis.get(REDIS_KEY_GLUCKY_NOW)
			b <- redis.hget(REDIS_KEY_GLUCKY, a.get.utf8String)
			c <- redis.hgetall(REDIS_KEY_GLUCKY_HISTORY + a.get.utf8String)
		} yield {
			val now = System.currentTimeMillis

			val ee = c.map { k => (k._1, s"""{"ts":${k._1},"h":"${JSONFormat.quoteString(k._2.utf8String.replaceAll("\n",""))}"}""")}.toArray
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(s"""{"data":${b.get.utf8String},"history":${ooo}}""")
		}
	}


	def getWinnerList(uid : String) = Action.async {
		redis.hgetall(REDIS_KEY_GLUCKY).map { s => //Future[Map[String, R]]
			Ok(s.filter { dd =>
				val data = dd._2.utf8String
				val ff = mapper.readTree(data)
				
				if(ff.get("finish").asBoolean && 
					System.currentTimeMillis - ff.get("winner_ts").asLong > DAY_20)
					redis.hdel(REDIS_KEY_GLUCKY, dd._1)

				ff.get("finish").asBoolean
			}.map { dd =>
				val data = dd._2.utf8String
				val ff = mapper.readTree(data)

				(data, ff.get("winner_ts").asLong)
			}.toList.sortBy(_._2).reverse.map( _._1 ).mkString("[", ",", "]"))
		}
	}	

	def updateMsg(uid : String, tid : String, msg : String) = Action.async {
		for {
			s <- redis.hget(REDIS_KEY_GLUCKY, tid) //Future[Option[R]]
		} yield {
			if(s.isDefined) {
				val data = s.get.utf8String
				val ff = mapper.readTree(data)
				val now = System.currentTimeMillis

				if(ff.get("finish").asBoolean) {
					val lgid = ff.get("lgid").asText

					if(uid != ff.get("winner_id").asText) {
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
							"big":${ff.get("big").asInt},
							"small":${ff.get("small").asInt},
							"finish":${ff.get("finish").asBoolean},
							"winner_id":"${ff.get("winner_id").asText}",
							"winner_ts":${ff.get("winner_ts").asLong},
							"winner_msg":"${msg}"
						}"""							
						redis.hset(REDIS_KEY_GLUCKY, ff.get("id").asText, result)
						Ok("")					
					}
				} else Ok("err:2")
			} else {
				Ok("err:1")
			} 						
		}
	}


	def play1(uid : String, tid : String, mynumber : Int, quick : Boolean, msg : String) = Action.async {
		for {
			s <- redis.hget(REDIS_KEY_GLUCKY, tid) //Future[Option[R]]
			c <- redis.hget(REDIS_KEY_GLUCKY_ANS, tid)
			d <- redis.get(REDIS_KEY_GLUCKY_NOW)
			tt <- redis.hget(REDIS_KEY_GLUCKY_CHECK_TIME, uid)
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
			} else if(!d.isDefined || d.get.utf8String != tid) { 
				Ok("err:3")
			} else if(s.isDefined) {
				val data = s.get.utf8String
				val ff = mapper.readTree(data)
				val now = System.currentTimeMillis

				if(!ff.get("finish").asBoolean) {
					val jjCount = ff.get("join_count").asInt + 1
					val lgid = ff.get("lgid").asText

					if(c.get.utf8String.toInt == mynumber) {
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
							"big":${mynumber},
							"small":${mynumber},
							"finish":true,							
							"winner_id":"${uid}",
							"winner_ts":${System.currentTimeMillis},
							"winner_msg":null
						}"""							
						redis.hset(REDIS_KEY_GLUCKY, ff.get("id").asText, result)
						redis.hset(REDIS_KEY_GLUCKY_HISTORY + tid, now.toString, uid + "##" + mynumber + "##" + JSONFormat.quoteString(msg))						
						redis.set(REDIS_KEY_GLUCKY_NOW_FINISH, "Y")

						if(quick) {
							redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
						}	

						Akka.system.scheduler.scheduleOnce(300 second) {
							createFinalGlucky()
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
							"big":${ff.get("big").asInt},
							"small":${ff.get("small").asInt},
							"finish":false,							
							"winner_id":null,
							"winner_ts":0,
							"winner_msg":""
						}"""

						for {
							aa <- redis.hset(REDIS_KEY_GLUCKY, tid, result)
							aa1 <- redis.hset(REDIS_KEY_GLUCKY_HISTORY + tid, now.toString, uid + "##" + mynumber + "##" + msg)
							aa2 <- redis.hset(REDIS_KEY_GLUCKY_CHECK_TIME, uid, now + NEXT_TIME + new java.util.Random().nextInt(1800000))
							aa3 <- if(quick) redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
									else Future.successful(true)
						} {
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