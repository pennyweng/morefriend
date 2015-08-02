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

object FinalNumberApplication extends Controller {
	implicit val system = Akka.system
	val redis = BidApplication.redis

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val NEXT_TIME = 3600000 * 3l

	val REDIS_KEY_FN = "RKFN"
	val REDIS_KEY_FN_HISTORY = "RKFHIS"
	val REDIS_KEY_FN_NOW = "RKFNOW"
	val REDIS_KEY_FN_MAX_VALUE = "RKFMV"
	val REDIS_KEY_FN_FINNAL_UID = "RKFFU"
	val REDIS_KEY_FN_CHECK_TIME = "RKFCT"
	val REDIS_KEY_FN_NOW_FINISH = "RKFNF"
	val REDIS_KEY_FN_WINNER = "RKFWNER"

	val BIG_NUM = 9999999999999999l
	val SMALL_NUM = 0l
	val DAY_20 = 86400000 * 20	

	def getPlayFnNextTime(uid :String, gid : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_FN_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current gid:${gid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss =  tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}


	def createFn(uid : String) = Action.async { request =>
		Logger.info(s"create Fial number uid:" + uid)

		if(uid == "29FF1053B1E30818A1131500269B50549B5C7285") {
			createNewFinal().map { result =>
				Ok(result)
			}
		} else {
			Logger.info("forbid to create final number uid :" + uid)
			Future.successful(Ok("error"))
		}

		// val gid = java.util.UUID.randomUUID.toString
		// val now = System.currentTimeMillis

		// for {
		// 	a <- redis.hkeys(REDIS_KEY_GIFT_LINE)
		// 	b <- redis.hget(REDIS_KEY_GIFT_LINE, a(new java.util.Random().nextInt(a.size)))
		// } yield {
		// 	val cc = new java.util.Random().nextLong().abs.toString.take(BIG_NUM.toString.length)
		// 	val kk = mapper.readTree(b.get.utf8String)
		// 	val result = s"""{
		// 		"id":"${gid}",
		// 		"type":"FR_LINE",
		// 		"lgid":"${kk.get("id").asText}",
		// 		"name":"${kk.get("name").asText}",
		// 		"big":${BIG_NUM},
		// 		"small":${SMALL_NUM},
		// 		"img":"${kk.get("img").asText}",
		// 		"start":${now},
		// 		"winner_id":null,
		// 		"winner_ts":0,
		// 		"winner_msg":null
		// 	}"""		
		// 	redis.hset(REDIS_KEY_FN, gid, result)	
		// 	redis.set(REDIS_KEY_FN_NOW, gid)
		// 	redis.set(REDIS_KEY_FN_MAX_VALUE, cc)	
		// 	redis.set(REDIS_KEY_FN_NOW_FINISH, "N")
		// 	Ok(result)					
		// }
	}




	// def getFnHistory(uid : String, gid : String) = Action.async {
	// 	Logger.info(s"getBidHistory uid:${uid}, gid:${gid}")

	// 	redis.hgetall(REDIS_KEY_FN_HISTORY + gid).map { e => //Future[Map[String, R]]
	// 		val ee = e.map { k => (k._1, s"""{"ts":${k._1},"h":"${k._2.utf8String}"}""")}.toArray
	// 		scala.util.Sorting.quickSort(ee)
	// 		val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
	// 		Ok(ooo)
	// 	}
	// }

	def getCurrentFn(uid : String) = Action.async {
		for {
			a <- redis.get(REDIS_KEY_FN_NOW)
			b <- redis.hget(REDIS_KEY_FN, a.get.utf8String)
			c <- redis.hgetall(REDIS_KEY_FN_HISTORY + a.get.utf8String)
			e <- redis.get(REDIS_KEY_FN_NOW_FINISH)
		} yield {
			val now = System.currentTimeMillis

			val ee = c.map { k => (k._1, s"""{"ts":${k._1},"h":"${k._2.utf8String}"}""")}.toArray
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(s"""{"data":${b.get.utf8String},"history":${ooo}}""")
		}
	}


	def updateMsg(gid : String, uid : String, msg : String) = Action.async {
		val now = System.currentTimeMillis

		for {
			a <- redis.hget(REDIS_KEY_FN_WINNER, gid)
			d <- redis.hget(REDIS_KEY_FN, gid)
		} yield {
			val ff = mapper.readTree(d.get.utf8String)

			if(!a.isDefined || (a.isDefined && a.get.utf8String != uid)) {
				Ok("err:1")
			} else {
				val result = s"""{
					"id":"${ff.get("id").asText}",
					"type":"${ff.get("type").asText}",
					"lgid":"${ff.get("lgid").asText}",
					"name":"${ff.get("name").asText}",
					"big":${ff.get("big").asLong},
					"small":${ff.get("small").asLong},
					"img":"${ff.get("img").asText}",
					"start":${ff.get("start").asLong},
					"winner_id":"${ff.get("winner_id").asText}",
					"winner_ts":${ff.get("winner_ts").asLong},
					"winner_msg":"${JSONFormat.quoteString(msg)}"
				}"""
				redis.hset(REDIS_KEY_FN, gid, result)
				Ok("")
			}
		}
	}


	def getWinner(uid : String) = Action.async {
		// redis.hgetall(REDIS_KEY_FN).map {  dd => //Future[Map[String, R]]
		// 	Ok(dd.map { _._2.utf8String }.filter {  gg =>
				
		// 		try {
		// 			val ff = mapper.readTree(gg)
		// 			// ff.get("winner_ts").asLong != 0
		// 			true
		// 		} catch {
		// 			case e => 
		// 				println("gg:" + gg)
		// 				false
		// 		}
		// 	}.toList.sortBy { gg =>
		// 		val ff = mapper.readTree(gg)
		// 			ff.get("start").asLong
		// 	}.reverse.mkString("[", ",", "]"))
		// }

		for {
			a <- redis.hgetall(REDIS_KEY_FN_WINNER) //Future[Map[String, R]]
			b <- redis.hmget(REDIS_KEY_FN, a.keys.toList : _*)	// Future[Seq[Option[R]]]		
		} yield {
			Ok(b.flatten.map { _.utf8String }.sortBy { gg =>
					val ff = mapper.readTree(gg)

					// if(System.currentTimeMillis - ff.get("start").asLong > DAY_20)
					ff.get("start").asLong
				}.reverse.take(30).mkString("[", ",", "]"))
		}
	}


	def createNewFinal() = {
		val gid = java.util.UUID.randomUUID.toString
		val now = System.currentTimeMillis

		for {
			e <- redis.get(REDIS_KEY_FN_NOW_FINISH)
			a <- redis.hkeys(REDIS_KEY_GIFT_LINE)
			b <- redis.hget(REDIS_KEY_GIFT_LINE, a(new java.util.Random().nextInt(a.size)))
		} yield {
			if(e.isDefined && e.get.utf8String == "Y") {
				val cc = new java.util.Random().nextLong().abs.toString.take(BIG_NUM.toString.length)
				val kk = mapper.readTree(b.get.utf8String)
				val result = s"""{
					"id":"${gid}",
					"type":"FR_LINE",
					"lgid":"${kk.get("id").asText}",
					"name":"${kk.get("name").asText}",
					"big":${BIG_NUM},
					"small":${SMALL_NUM},
					"img":"${kk.get("img").asText}",
					"start":${now},
					"winner_id":null,
					"winner_ts":0,
					"winner_msg":null
				}"""		
				redis.hset(REDIS_KEY_FN, gid, result)	
				redis.set(REDIS_KEY_FN_NOW, gid)
				redis.set(REDIS_KEY_FN_MAX_VALUE, cc)	
				redis.set(REDIS_KEY_FN_NOW_FINISH, "N")
				result
			} else "error"				
		}		
	}

	// def checkNewFinal() {
	// 	for {
	// 		a <- redis.get(REDIS_KEY_FN_NOW)
	// 		e <- redis.get(REDIS_KEY_FN_NOW_FINISH)
	// 		c <- redis.hexists(REDIS_KEY_FN_WINNER, a.get.utf8String)
	// 	} {
	// 		if(e.isDefined && e.get.utf8String == "Y" && c) {
	// 			Logger.info("checkNewFinal")
	// 			createNewFinal()
 // 			} 
	// 	}
	// }

	def playNewFn(gid : String, uid : String, guess : Long, quick : Boolean) = Action.async {
		val now = {
			System.currentTimeMillis.toString.dropRight(3) + (100 + new java.util.Random().nextInt(899)).toString
		}.toLong

		for {
			a <- redis.get(REDIS_KEY_FN_NOW) // is running
			b <- redis.hget(PointApplication.REDIS_KEY_POINT, uid) // uid point
			c <- redis.get(REDIS_KEY_FN_MAX_VALUE) // is bigger than max
			d <- redis.hget(REDIS_KEY_FN, gid) // is end 
			tt <- redis.hget(REDIS_KEY_FN_CHECK_TIME, uid)
			e <- redis.get(REDIS_KEY_FN_NOW_FINISH)
		} yield {
			val ff = mapper.readTree(d.get.utf8String)
			
			val big = ff.get("big").asLong
			val small = ff.get("small").asLong
			val lgid = ff.get("lgid").asText

			val userPoint =if(b.isDefined) {
				 b.get.utf8String.toLong
			} else {
				0l
			}

			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				val sss = tt.get.utf8String.toLong -now
				Ok("err:5:" + sss)
			} else if(!a.isDefined || a.get.utf8String != gid) {
				Ok("err:1")
			} else if(e.isDefined && e.get.utf8String == "Y") {
				Ok("err:4")	
 			} else if(quick && userPoint < 50) {
				Ok("err:2")
			} else if(c.isDefined && c.get.utf8String.toLong == guess) {
				redis.set(REDIS_KEY_FN_NOW_FINISH, "Y")
				redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
				val code = GiftApplication.getCode(lgid, uid)
				redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)					
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
				redis.hset(REDIS_KEY_FN_WINNER, gid, uid)
				redis.hset(REDIS_KEY_FN_HISTORY + gid, now.toString , uid + "##" + guess)

				val result = s"""{
					"id":"${ff.get("id").asText}",
					"type":"${ff.get("type").asText}",
					"lgid":"${ff.get("lgid").asText}",
					"name":"${ff.get("name").asText}",
					"big":${guess},
					"small":${guess},
					"img":"${ff.get("img").asText}",
					"start":${ff.get("start").asLong},
					"winner_id":"${uid}",
					"winner_ts":${now},
					"winner_msg":null
				}"""
				redis.hset(REDIS_KEY_FN, gid, result)
				
				if(quick) {
					redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
				}

				Akka.system.scheduler.scheduleOnce(300 second) {
					createNewFinal()
				}

				Ok(s"${guess}##${guess}#winner")			
			} else if(!c.isDefined || guess >= big || guess <= small) {
				Ok(s"err:3:${small}##${big}")
			} else {
				val ans = c.get.utf8String.toLong
				val newSmall = if(guess < ans) guess else small
				val newBig = if(guess > ans) guess else big
				
				if(newBig - newSmall >= 17) {
					redis.hset(REDIS_KEY_FN_HISTORY + gid, now.toString , uid + "##" + guess)
					val result = s"""{
						"id":"${ff.get("id").asText}",
						"type":"${ff.get("type").asText}",
						"lgid":"${ff.get("lgid").asText}",
						"name":"${ff.get("name").asText}",
						"big":${newBig},
						"small":${newSmall},
						"img":"${ff.get("img").asText}",
						"start":${ff.get("start").asLong},
						"winner_id":null,
						"winner_ts":0,
						"winner_msg":null
					}"""
					redis.hset(REDIS_KEY_FN, gid, result)
				}
				
				val nextPlay = now + NEXT_TIME
				redis.hset(REDIS_KEY_FN_CHECK_TIME, uid, nextPlay)
				
				if(quick) {
					redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
				}

				Ok(s"${newSmall}##${newBig}")
			}
		}
	}	

		
}
