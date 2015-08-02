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

object PointApplication extends Controller {
	implicit val system = Akka.system

	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))
	val reportStartTime = 0l;

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
	
	val REDIS_KEY_POINT = "RKP"
	val REDIS_KEY_POINT_REC = "RKPR:"
	val REDIS_KEY_POINT_TOTAL = "RKPT:"
	val REDIS_KEY_POINT_DAILY = "RKPD"
	val REDIS_KEY_POINT_FACEBOOK_PROMOTION = "RKPFR"
	val FB_POINT = 66

 // {
 //        "id": "ecf1b24d-c4b9-4688-b545-b2e35e5e79bf",
 //        "ts": 1434720245896,
 //        "from": "2",
 //        "point": 50
 //    }

	def getPointHistory(uid : String) = Action.async {
		redis.hgetall(REDIS_KEY_POINT_REC + uid).map { item => //Future[Map[String, R]]
			Ok(item.map { _._2.utf8String }.map { ee =>
				val aa = mapper.readTree(ee)
				(aa.get("ts").asLong, ee)
			}.toList.sortBy( _._1).takeRight(100).reverse.map {_._2}.mkString("[", ",", "]"))
		}
	}

	def reportTotal(uid : String, totalPoint : Long, from : String) = Action.async {
		redis.hget(REDIS_KEY_POINT_TOTAL + from, uid).map { pp =>
			val total =if(pp.isDefined) {
				 totalPoint - pp.get.utf8String.toLong
			} else {
				totalPoint
			}
			if(total > 0) {
				redis.hset(REDIS_KEY_POINT_TOTAL + from, uid, totalPoint)
				earnInternalPoint(uid, total, from)
			}
			Ok("")
		}
	}

	def isPromoteFB(uid : String) = Action.async {
		redis.hexists(REDIS_KEY_POINT_FACEBOOK_PROMOTION, uid).map { isExist =>
			if(!isExist) {
				Ok("")
			} else {
				Ok("err:1")
			}
		}
	}

	def promoteFB(uid : String) = Action.async {
		for {
			isExist <- redis.hexists(REDIS_KEY_POINT_FACEBOOK_PROMOTION, uid)
			vv <- if(!isExist) {
					earnInternalPoint(uid, FB_POINT, "5") 
				} else Future.successful("")
		} yield {
			if(!isExist) {
				redis.hset(REDIS_KEY_POINT_FACEBOOK_PROMOTION, uid, System.currentTimeMillis.toString)
				Ok(vv)
			} else {
				Ok("err:1")
			}
		}
	}


	def earnInternalPoint(uid :String, point : Long, from : String, product : String = "") = {
		redis.hget(REDIS_KEY_POINT, uid).map { pp =>
			val total =if(pp.isDefined) {
				 pp.get.utf8String.toLong + point
			} else {
				point
			}

			redis.hset(REDIS_KEY_POINT, uid, total.toString)

			val id = java.util.UUID.randomUUID.toString

			 val rr = s"""{"id":"${id}", "ts":${System.currentTimeMillis}, "from":"${from}", "point":${point}, "product":"${product}"}"""
			redis.hset(REDIS_KEY_POINT_REC + uid, id, rr)
			total.toString + "#" + point
		}
	}

	def checkTodayClick(uid : String) = {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_POINT_DAILY, uid).map { pp =>
			val preTime =if(pp.isDefined) {
				 pp.get.utf8String.toLong 
			} else {
				0
			}
			now - preTime > 86400000
		}
	}

	def checkTodayClick1(uid : String) =  Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_POINT_DAILY, uid).map { pp =>
			val preTime =if(pp.isDefined) {
				 pp.get.utf8String.toLong 
			} else {
				0
			}
			if(now - preTime > 86400000) Ok("0") 
			else { 
				val ss = 86400000 - (now - preTime)
				Ok(ss + "")
			} 
		}
	}


	def earnDailyPoint(uid :String) = Action.async {
		val now = System.currentTimeMillis

		for {
			isOk <- checkTodayClick(uid)
			vv <- if(isOk) {
					val nnp = 3 + new java.util.Random().nextInt(35).toLong
					earnInternalPoint(uid, nnp, "4") 
				} else Future.successful("")
		} yield {
			if(isOk) {
				redis.hset(REDIS_KEY_POINT_DAILY, uid, now.toString)
				Ok(vv)
			} else {
				Ok("err:1")
			}
		}
	}

	def earnPoint(uid :String, point : Long, from : String) = Action.async {
		earnInternalPoint(uid, point, from).map { ss =>
			Ok(ss)
		}
	}

	// import java.net.URLDecoder
	val REDIS_KEY_YUMI_ORDER = "RKYOU"
	def earnFromYumi(order : String, user : String, points : String, ad : String) = Action.async {
		redis.hexists(REDIS_KEY_YUMI_ORDER, user + "_" + order).map { exist =>
			if(exist) {
				Logger.info("it alway exist")
				Ok("exist")
			} else {
				earnInternalPoint(user, points.toLong, "2", ad)
				Ok("")
			}
		}
	}

	val REDIS_KEY_ADPLAY_ORDER = "RKADU"
	def earnFromAdplay(achieve_id : String, identifier : String, point : String, campaign_name : String) = Action.async {
		redis.hexists(REDIS_KEY_ADPLAY_ORDER, identifier + "_" + achieve_id).map { exist =>
			if(exist) {
				Logger.info("it alway exist")
				Ok("0")
			} else {
				earnInternalPoint(identifier, point.toLong, "6", campaign_name)
				Ok("1")
			}
		}
	}
	

	def getPoint(uid : String) = Action.async {
		redis.hget(REDIS_KEY_POINT, uid).map { pp =>
			val total =if(pp.isDefined) {
				 pp.get.utf8String.toLong
			} else {
				0l
			}
			Ok(total.toString)
		}
	}

	def consumeLine(uid : String, lgid : String) = Action.async {
		val now = System.currentTimeMillis

		for {
			pp <- redis.hget(REDIS_KEY_POINT, uid)
			ll <- redis.hget(REDIS_KEY_GIFT_LINE, lgid)
		} yield {
			val total =if(pp.isDefined) {
				 pp.get.utf8String.toLong
			} else {
				0l
			}

			val maxPoint = if(ll.isDefined) {
				val jj = mapper.readTree(ll.get.utf8String)
				Some(jj.get("point").asLong)			
			} else None

			if(!maxPoint.isDefined) {
				Ok("err:1")
			} else if(total < maxPoint.get) { 
				Ok("err:2")
			} else {
				val llss = total - maxPoint.get

				redis.hset(REDIS_KEY_POINT, uid, llss.toString)
				redis.hset(REDIS_KEY_GIFT_LINE_CHECK_TIME + lgid, uid, now)
				val code = GiftApplication.getCode(lgid, uid)
				redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				redis.sadd(REDIS_KEY_GIFT_LINE_ACHIVE+uid, lgid)
				Ok(llss.toString)
			}
		}
	}

	def consumeMoney(uid : String, lgid : String) = Action.async {
		val now = System.currentTimeMillis

		for {
			pp <- redis.hget(REDIS_KEY_POINT, uid)
			ll <- redis.hget(REDIS_KEY_GIFT_MONEY, lgid)
		} yield {
			val total =if(pp.isDefined) {
				 pp.get.utf8String.toLong
			} else {
				0l
			}

			val maxPoint = if(ll.isDefined) {
				val jj = mapper.readTree(ll.get.utf8String)
				Some(jj.get("point").asLong)			
			} else None

			if(!maxPoint.isDefined) {
				Ok("err:1")
			} else if(total < maxPoint.get) { 
				Ok("err:2")
			} else {
				val llss = total - maxPoint.get

				redis.hset(REDIS_KEY_POINT, uid, llss.toString)
				redis.hset(REDIS_KEY_GIFT_MONEY_CHECK_TIME + lgid, uid, now)
				val code = GiftApplication.getCode(lgid, uid)
				redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				redis.sadd(REDIS_KEY_GIFT_MONEY_ACHIVE+uid, lgid)
				Ok(llss.toString)
			}
		}
	}	

	def consumeBag(uid : String, lgid : String) = Action.async {
		val now = System.currentTimeMillis

		for {
			pp <- redis.hget(REDIS_KEY_POINT, uid)
			ll <- redis.hget(REDIS_KEY_GIFT_BAG, lgid)
		} yield {
			val total =if(pp.isDefined) {
				 pp.get.utf8String.toLong
			} else {
				0l
			}

			val maxPoint = if(ll.isDefined) {
				val jj = mapper.readTree(ll.get.utf8String)
				Some(jj.get("point").asLong)			
			} else None

			if(!maxPoint.isDefined) {
				Ok("err:1")
			} else if(total < maxPoint.get) { 
				Ok("err:2")
			} else {
				val llss = total - maxPoint.get

				redis.hset(REDIS_KEY_POINT, uid, llss.toString)
				redis.hset(REDIS_KEY_GIFT_BAG_CHECK_TIME + lgid, uid, now)
				val code = GiftApplication.getCode(lgid, uid)
				redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				redis.sadd(REDIS_KEY_GIFT_BAG_ACHIVE+uid, lgid)
				Ok(llss.toString)
			}
		}
	}	


	def consumeSe(uid : String, lgid : String) = Action.async {
		val now = System.currentTimeMillis

		for {
			pp <- redis.hget(REDIS_KEY_POINT, uid)
			ll <- redis.hget(REDIS_KEY_GIFT_SE, lgid)
		} yield {
			val total =if(pp.isDefined) {
				 pp.get.utf8String.toLong
			} else {
				0l
			}

			val maxPoint = if(ll.isDefined) {
				val jj = mapper.readTree(ll.get.utf8String)
				Some(jj.get("point").asLong)			
			} else None

			if(!maxPoint.isDefined) {
				Ok("err:1")
			} else if(total < maxPoint.get) { 
				Ok("err:2")
			} else {
				val llss = total - maxPoint.get

				redis.hset(REDIS_KEY_POINT, uid, llss.toString)
				redis.hset(REDIS_KEY_GIFT_SE_CHECK_TIME + lgid, uid, now)
				val code = GiftApplication.getCode(lgid, uid)
				redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				redis.sadd(REDIS_KEY_GIFT_SE_ACHIVE+uid, lgid)
				Ok(llss.toString)
			}
		}
	}

	def consumeLineTopic(uid : String, lgid : String) = Action.async {
		val now = System.currentTimeMillis

		for {
			pp <- redis.hget(REDIS_KEY_POINT, uid)
			ll <- redis.hget(REDIS_KEY_GIFT_LINE_TOPIC, lgid)
		} yield {
			val total =if(pp.isDefined) {
				 pp.get.utf8String.toLong
			} else {
				0l
			}

			val maxPoint = if(ll.isDefined) {
				val jj = mapper.readTree(ll.get.utf8String)
				Some(jj.get("point").asLong)			
			} else None

			if(!maxPoint.isDefined) {
				Ok("err:1")
			} else if(total < maxPoint.get) { 
				Ok("err:2")
			} else {
				val llss = total - maxPoint.get

				redis.hset(REDIS_KEY_POINT, uid, llss.toString)
				redis.hset(REDIS_KEY_GIFT_LINE_TOPIC_CHECK_TIME + lgid, uid, now)
				val code = GiftApplication.getCode(lgid, uid)
				redis.hset(REDIS_KEY_GIFT_RANDOM_KEY, lgid + "_" + uid , code._1)
				redis.hset(REDIS_KEY_GIFT_CODE_KEY, lgid + "_" + uid , code._2)
				redis.sadd(REDIS_KEY_GIFT_LINE_TOPIC_ACHIVE+uid, lgid)
				Ok(llss.toString)
			}
		}
	}

	def getParam(uid : String) = Action {
		Ok("""{"g_ad":76400000,"ad_hodo_enable":true,"ad_mob_159_enable":true,"ad_locus_enable":true}""")
	}	
}