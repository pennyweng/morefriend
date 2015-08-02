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


object BidApplication extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val NEXT_TIME = 3600000 * 2l
	// val NEXT_TIME = 2l

	val REDIS_KEY_BID = "RKBID"
	val REDIS_KEY_BID_HISTORY = "RKBHIS"
	val REDIS_KEY_BID_NOW = "RKBNOW"
	val REDIS_KEY_BID_MAX_VALUE = "RKBMV"
	val REDIS_KEY_BID_FINNAL_UID = "RKBFU"
	val REDIS_KEY_BID_CHECK_TIME = "RKBCT"
	val REDIS_KEY_BID_NOW_FINISH = "RKBNF"
	val REDIS_KEY_BID_WINNER = "RKBWINER"
	val BID_DAY = 3
	val BID_BASE_COUNT = 200

	def getPlayBidNextTime(uid :String, bid : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_BID_CHECK_TIME, uid).map { tt =>
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				Logger.info(s"time is not over,current bid:${bid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss =  tt.get.utf8String.toLong - now
				Ok(sss + "")
			} else Ok("0")
		}
	}


	def createBid() = Action.async { request =>
		Logger.info(s"create bid")

		val body = request.body
		val data = body.asText.getOrElse("")
		val bid = java.util.UUID.randomUUID.toString
		val now = System.currentTimeMillis
		val end = now + (BID_DAY * 86400000)
		// val end = now + 180000

		if(data == "") {
			for {
				a <- redis.hkeys(REDIS_KEY_GIFT_LINE)
				b <- redis.hget(REDIS_KEY_GIFT_LINE, a(new java.util.Random().nextInt(a.size)))
			} yield {
				val kk = mapper.readTree(b.get.utf8String)
				val result = s"""{
					"id":"${bid}",
					"type":"FR_LINE",
					"lgid":"${kk.get("id").asText}",
					"name":"${kk.get("name").asText}",
					"base_count":${BID_BASE_COUNT},
					"img":"${kk.get("img").asText}",
					"end":${end},
					"start":${now}
				}"""		
				redis.hset(REDIS_KEY_BID, bid, result)	
				redis.set(REDIS_KEY_BID_NOW, bid)
				redis.set(REDIS_KEY_BID_MAX_VALUE, BID_BASE_COUNT.toString)	
				redis.set(REDIS_KEY_BID_NOW_FINISH, "N")
				Ok(result)					
			}
		} else {
			val ff = mapper.readTree(data)
			val result = s"""{
				"id":"${bid}",
				"lgid":"${ff.get("lgid").asText}",
				"name":"${ff.get("name").asText}",
				"base_count":${ff.get("base_count").asInt},
				"img":"${ff.get("img").asText}",
				"end":${end},
				"start":${now}
			}"""		
			for {
				a <- redis.hset(REDIS_KEY_BID, bid, result)
				b <- redis.set(REDIS_KEY_BID_NOW, bid)
				c <- redis.set(REDIS_KEY_BID_MAX_VALUE, ff.get("base_count").asInt.toString)
				d <- redis.set(REDIS_KEY_BID_NOW_FINISH, "N")
			} yield {
				Ok(result)
			}
		}
	}


	def getBidHistory(uid : String, bid : String) = Action.async {
		Logger.info(s"getBidHistory uid:${uid}, bid:${bid}")

		redis.hgetall(REDIS_KEY_BID_HISTORY + bid).map { e => //Future[Map[String, R]]
			val ee = e.map { k => (k._1, s"""{"ts":${k._1},"h":"${k._2.utf8String}"}""")}.toArray
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)
		}
	}

	def getWinner(uid : String) = Action.async {
		for {
			a <- redis.hgetall(REDIS_KEY_BID_WINNER) //Future[Map[String, R]]
			b <- redis.hmget(REDIS_KEY_BID, a.keys.toList : _*)	// Future[Seq[Option[R]]]		
		} yield {
			Ok(b.flatten.map { _.utf8String }.mkString("[", ",", "]"))
		}
	}


	def getCurrentBid(uid : String) = Action.async {
		for {
			a <- redis.get(REDIS_KEY_BID_NOW)
			b <- redis.hget(REDIS_KEY_BID, a.get.utf8String)
			c <- redis.hgetall(REDIS_KEY_BID_HISTORY + a.get.utf8String)
			d <- redis.get(REDIS_KEY_BID_MAX_VALUE)
			e <- redis.get(REDIS_KEY_BID_NOW_FINISH)
		} yield {
			val now = System.currentTimeMillis
			val endTime = {
				val ff = mapper.readTree(b.get.utf8String)
				ff.get("end").asLong
			}

			if(now >= endTime && e.isDefined && e.get.utf8String == "N") {
				doF(a.get.utf8String)
			}

			val ee = c.map { k => (k._1, s"""{"ts":${k._1},"h":"${JSONFormat.quoteString(k._2.utf8String)}"}""")}.toArray
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(s"""{"data":${b.get.utf8String},"history":${ooo},"now_base":${d.get.utf8String}, "final":${now >= endTime}}""")
		}
	}

	def doF(bid : String) = {
		val now = System.currentTimeMillis

		for {
			a <- redis.hget(REDIS_KEY_BID_FINNAL_UID, bid)
			b <- if(a.isDefined) redis.hget(PointApplication.REDIS_KEY_POINT, a.get.utf8String) else Future.successful(None)
			c <- redis.hget(REDIS_KEY_BID, bid)
			d <- redis.get(REDIS_KEY_BID_MAX_VALUE)
		} yield {
			if(a.isDefined && b.isDefined && d.isDefined) {
				val ff = mapper.readTree(c.get.utf8String)
				val lgid = ff.get("lgid").asText
				val uid = a.get.utf8String
				val userTotal = b.get.utf8String.toLong
				val payTotal = d.get.utf8String.toLong

				if(userTotal != 0 && userTotal >= payTotal) {
					val llss = userTotal - payTotal

					redis.hset(PointApplication.REDIS_KEY_POINT, uid, llss.toString)
					redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
					val code = GiftApplication.getCode(lgid, uid)
					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)					
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
					redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
					redis.set(REDIS_KEY_BID_NOW_FINISH, "Y")
					redis.hset(REDIS_KEY_BID_WINNER, bid, uid)
				}		
			}
		}		
	}


	def doFinalBid(bid : String) = Action.async {
		val now = System.currentTimeMillis

		for {
			a <- redis.hget(REDIS_KEY_BID_FINNAL_UID, bid)
			b <- if(a.isDefined) redis.hget(PointApplication.REDIS_KEY_POINT, a.get.utf8String) else Future.successful(None)
			c <- redis.hget(REDIS_KEY_BID, bid)
			d <- redis.get(REDIS_KEY_BID_MAX_VALUE)
		} yield {
			if(a.isDefined && b.isDefined && d.isDefined) {
				val ff = mapper.readTree(c.get.utf8String)
				val lgid = ff.get("lgid").asText
				val uid = a.get.utf8String
				val userTotal = b.get.utf8String.toLong
				val payTotal = d.get.utf8String.toLong

				if(userTotal != 0 && userTotal >= payTotal) {
					val llss = userTotal - payTotal

					redis.hset(PointApplication.REDIS_KEY_POINT, uid, llss.toString)
					redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
					val code = GiftApplication.getCode(lgid, uid)
					redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)					
					redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
					redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
					redis.set(REDIS_KEY_BID_NOW_FINISH, "Y")

					Ok("do final ok")
				} else Ok("err:2")				
			}  else Ok("err:1")
		}
	}

	def playNewBid(bid : String, uid : String, bidValue : Int, nickName : String, quick : Boolean) = Action.async {
		val now = System.currentTimeMillis

		for {
			a <- redis.get(REDIS_KEY_BID_NOW) // is running
			b <- redis.hget(PointApplication.REDIS_KEY_POINT, uid) // uid point
			c <- redis.get(REDIS_KEY_BID_MAX_VALUE) // is bigger than max
			d <- redis.hget(REDIS_KEY_BID, bid) // is end 
			tt <- redis.hget(REDIS_KEY_BID_CHECK_TIME, uid)
		} yield {
			val userPoint =if(b.isDefined) {
				 b.get.utf8String.toLong
			} else {
				0l
			}
			val bidV = if(quick) 50 + bidValue else bidValue

			val endTime = {
				val ff = mapper.readTree(d.get.utf8String)
				ff.get("end").asLong
			}
			if(tt.isDefined && now < tt.get.utf8String.toLong) {
				val sss = tt.get.utf8String.toLong -now
				Ok("err:5:" + sss)
			} else if(!a.isDefined || a.get.utf8String != bid) {
				Ok("err:1")
			} else if(bidV > userPoint) {
				Ok("err:2")
			} else if(!c.isDefined || c.get.utf8String.toInt >= bidValue) {
				Ok("err:3:" + c.get.utf8String.toInt)
			} else if(endTime <= now) {
				Ok("err:4")
			} else {
				redis.hset(REDIS_KEY_BID_HISTORY + bid, now.toString , uid + "##" + bidValue + "##" + nickName)
				redis.set(REDIS_KEY_BID_MAX_VALUE, bidValue)
				redis.hset(REDIS_KEY_BID_FINNAL_UID, bid, uid)
				val nextPlay = now + NEXT_TIME
				redis.hset(REDIS_KEY_BID_CHECK_TIME, uid, nextPlay)	


				if(quick) {
					redis.hset(PointApplication.REDIS_KEY_POINT, uid, (userPoint - 50).toString)
				}
				
				Ok("ok")
			}
		}
	}	
}
