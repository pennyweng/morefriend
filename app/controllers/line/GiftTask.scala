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


object GiftTask extends Controller {
	implicit val system = Akka.system

	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val redis = GiftApplication.redis
	val MAX_TASKS = 4


	def addTask(uid : String, lgid : String, giftType : String) = Action.async {
		Logger.info(s"addTask:lgid ${lgid}, uid:${uid}, giftType:${giftType}")

		redis.smembers(REDIS_KEY_GIFT_TASKS + uid).map { ll =>
			val taskIds = ll.map { _.utf8String }.toList
			if(taskIds.length >= MAX_TASKS) {
				Ok("err:1")
			} else if(taskIds.contains(lgid + "_" + giftType)) {
				Ok("err:2")
			} else {
				redis.sadd(REDIS_KEY_GIFT_TASKS + uid, lgid + "_" + giftType)
				Ok("")
			}
		}
	}

	def getTaskIds(uid : String) = {
		redis.smembers(REDIS_KEY_GIFT_TASKS + uid).map { ll =>
			ll.map { _.utf8String }.toList
		}
	}

	def getTask(uid : String, taskIds : List[String]) = {
		Future.sequence(taskIds.map { vv => 
			val lgid = vv.substring(0, vv.indexOf("_"))
			val giftType = vv.substring(vv.indexOf("_") + 1)
			val key = lgid + "_" + uid
			if(giftType == "FR_LINE") {
				for {
					gl <- redis.hget(REDIS_KEY_GIFT_LINE, lgid)
					pc <- redis.hget(REDIS_KEY_GIFT_LINE_PLAY_COUNT, key)
				} yield {
					(gl, pc)
				}
			} else if(giftType == "FR_MONY") {
				for {
					gl <- redis.hget(REDIS_KEY_GIFT_MONEY, lgid)
					pc <- redis.hget(REDIS_KEY_GIFT_MONEY_PLAY_COUNT, key)
				} yield {
					(gl, pc)
				}				
			} else if(giftType == "FR_BAG") {
				for {
					gl <- redis.hget(REDIS_KEY_GIFT_BAG, lgid)
					pc <- redis.hget(REDIS_KEY_GIFT_BAG_PLAY_COUNT, key)
				} yield {
					(gl, pc)
				}
			} else if(giftType == "FR_SE") {
				for {
					gl <- redis.hget(REDIS_KEY_GIFT_SE, lgid)
					pc <- redis.hget(REDIS_KEY_GIFT_SE_PLAY_COUNT, key)
				} yield {
					(gl, pc)
				}				
			} else Future.successful((None, None))
		}.toList)
	}

	def getTasks(uid : String) = Action.async {
		for {
			taskIds <- getTaskIds(uid)
			task <- getTask(uid, taskIds)
		} yield {
			Ok(task.flatMap { tt =>
				val currentCount = if(tt._2.isDefined) tt._2.get.utf8String.toInt else 0
				if(tt._1.isDefined) {
					Some(s"""{"ll":${tt._1.get.utf8String},"cc":${currentCount}}""")
				} else None
			}.mkString("[", ",", "]"))
		}
	}


	def removeTaks(uid : String, lgid : String, giftType : String) =  Action.async {
		redis.hdel(REDIS_KEY_GIFT_LINE_PLAY_COUNT, lgid + "_" + uid)
		redis.hdel(REDIS_KEY_GIFT_MONEY_PLAY_COUNT, lgid + "_" + uid)
		redis.hdel(REDIS_KEY_GIFT_BAG_PLAY_COUNT, lgid + "_" + uid)
		redis.hdel(REDIS_KEY_GIFT_SE_PLAY_COUNT, lgid + "_" + uid)

		redis.srem(REDIS_KEY_GIFT_TASKS + uid, lgid + "_" + giftType ).map { s =>
			Ok("")
		}
	}	
}