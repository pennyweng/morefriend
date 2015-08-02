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


case class  LeaveMsg @JsonCreator() ( 
	@scala.beans.BeanProperty @JsonProperty("msg") msg : String, 
	@scala.beans.BeanProperty @JsonProperty("ts") ts : Long, 
	@scala.beans.BeanProperty @JsonProperty("from") from : Int)

object GiftReportApplication extends Controller {
	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val redis = GiftApplication.redis

	def reportLineMsg(lgid : String, uid : String, msg : String) = Action.async {
		val key = lgid + "_" + uid
		val lmsg = LeaveMsg(msg, System.currentTimeMillis, 1)
		redis.hget(REDIS_KEY_GIFT_REPORT_LINE_MSG, key).map { ff =>
			if(ff.isDefined) {
				val lms = mapper.readValue[Array[LeaveMsg]](ff.get.utf8String, classOf[Array[LeaveMsg]])
				val newLms = Array(lmsg) ++ lms
				redis.hset(REDIS_KEY_GIFT_REPORT_LINE_MSG, key, mapper.writeValueAsString(newLms))
			} else {
				redis.hset(REDIS_KEY_GIFT_REPORT_LINE_MSG, key, mapper.writeValueAsString(Array(lmsg)))
			} 
			Ok("")
		}
	}

	def getReportLineMsg(lgid : String, uid : String) = Action.async {
		val key = lgid + "_" + uid
		redis.hget(REDIS_KEY_GIFT_REPORT_LINE_MSG, key).map { ff =>
			if(ff.isDefined) {
				Ok(ff.get.utf8String)
			} else {
				Ok("[]")	
			} 
		}
	}

 	def reportLine(lgid : String, uid : String, lid : String, lnick: String, lname: String, code : String) = Action.async {
 		val key = lgid + "_" + uid

		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_ACHIVE + uid) //Future[Seq[R]]
			isE <- redis.hexists(REDIS_KEY_GIFT_REPORT_LINE, key)
			code1 <- redis.hget(REDIS_KEY_GIFT_CODE_KEY, key)
			kk <- redis.hget(REDIS_KEY_GIFT_LINE, lgid)
		} yield {
			val achived = s1.map { _.utf8String }.toList
			if(!achived.contains(lgid)) {
				Ok("err:1")
			} else if(isE) {
				Ok("err:2")
			} else if(lid == "" || lname == "") {
				Ok("err:3")
			} else if(!code1.isDefined || (code1.isDefined && code1.get.utf8String != code)) {
				Ok("err:4")
			} else if(!kk.isDefined) {
				Ok("err:5")
			} else {
				val jj = mapper.readTree(kk.get.utf8String)
				val result = s"""{
					"id":"${key}",
					"uid":"${uid}",
					"lid":"${JSONFormat.quoteString(lid)}",
					"lnick":"${JSONFormat.quoteString(lnick)}",
					"lname":"${jj.get("name").asText}",
					"code":"${code}",
					"status":1,
					"line_img":"${jj.get("img").asText}",
					"cts":${System.currentTimeMillis},
					"mark":""
				}"""
				redis.hset(REDIS_KEY_GIFT_REPORT_LINE, key, result)
				Ok("")
			}
		}
	}

 	def reportLineTopic(lgid : String, uid : String, lid : String, lnick: String, lname: String, code : String) = Action.async {
 		val key = lgid + "_" + uid

		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_LINE_TOPIC_ACHIVE + uid) //Future[Seq[R]]
			isE <- redis.hexists(REDIS_KEY_GIFT_REPORT_LINE_TOPIC, key)
			code1 <- redis.hget(REDIS_KEY_GIFT_CODE_KEY, key)
			kk <- redis.hget(REDIS_KEY_GIFT_LINE_TOPIC, lgid)
		} yield {
			val achived = s1.map { _.utf8String }.toList
			if(!achived.contains(lgid)) {
				Ok("err:1")
			} else if(isE) {
				Ok("err:2")
			} else if(lid == "" || lname == "") {
				Ok("err:3")
			} else if(!code1.isDefined || (code1.isDefined && code1.get.utf8String != code)) {
				Ok("err:4")
			} else if(!kk.isDefined) {
				Ok("err:5")
			} else {
				val jj = mapper.readTree(kk.get.utf8String)
				val result = s"""{
					"id":"${key}",
					"uid":"${uid}",
					"lid":"${JSONFormat.quoteString(lid)}",
					"lnick":"${JSONFormat.quoteString(lnick)}",
					"lname":"${jj.get("name").asText}",
					"code":"${code}",
					"status":1,
					"line_img":"${jj.get("img").asText}",
					"cts":${System.currentTimeMillis},
					"mark":""
				}"""
				redis.hset(REDIS_KEY_GIFT_REPORT_LINE_TOPIC, key, result)
				Ok("")
			}
		}
	}

 	def reportMoney(lgid : String, uid : String, bankName : String, bankCode: String, account: String, accountName : String, code : String) = Action.async {
 		val key = lgid + "_" + uid

		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_MONEY_ACHIVE + uid) //Future[Seq[R]]
			isE <- redis.hexists(REDIS_KEY_GIFT_REPORT_MONEY, key)
			code1 <- redis.hget(REDIS_KEY_GIFT_CODE_KEY, key)
			kk <- redis.hget(REDIS_KEY_GIFT_MONEY, lgid)
		} yield {
			val achived = s1.map { _.utf8String }.toList
			if(!achived.contains(lgid)) {
				Ok("err:1")
			} else if(isE) {
				Ok("err:2")
			} else if(bankName == "" || bankCode == "" || account == "" || accountName == "" ) {
				Ok("err:3")
			} else if(!code1.isDefined || (code1.isDefined && code1.get.utf8String != code)) {
				Ok("err:4")
			} else if(!kk.isDefined) {
				Ok("err:5")
			} else {
				val jj = mapper.readTree(kk.get.utf8String)

				val result = s"""{
					"id":"${key}",
					"uid":"${uid}",
					"bank_name":"${bankName}",
					"bank_code":"${bankCode}",
					"account":"${account}",
					"account_name":"${accountName}",
					"lname":"${jj.get("name").asText}",
					"code":"${code}",
					"status":1,
					"line_img":"${jj.get("img").asText}",
					"cts":${System.currentTimeMillis},
					"mark":""
				}"""
				redis.hset(REDIS_KEY_GIFT_REPORT_MONEY, key, result)
				Ok("")
			}
		}
	}

 	def reportBag(lgid : String, uid : String, postcode : String, address: String, account: String, phone : String, code : String) = Action.async {
 		val key = lgid + "_" + uid

		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_BAG_ACHIVE + uid) //Future[Seq[R]]
			isE <- redis.hexists(REDIS_KEY_GIFT_REPORT_BAG, key)
			code1 <- redis.hget(REDIS_KEY_GIFT_CODE_KEY, key)
			kk <- redis.hget(REDIS_KEY_GIFT_BAG, lgid)
		} yield {
			val achived = s1.map { _.utf8String }.toList
			if(!achived.contains(lgid)) {
				Ok("err:1")
			} else if(isE) {
				Ok("err:2")
			} else if(postcode == "" || address == "" || account == "" || phone == "" ) {
				Ok("err:3")
			} else if(!code1.isDefined || (code1.isDefined && code1.get.utf8String != code)) {
				Ok("err:4")
			} else if(!kk.isDefined) {
				Ok("err:5")
			} else {
				val jj = mapper.readTree(kk.get.utf8String)

				val result = s"""{
					"id":"${key}",
					"uid":"${uid}",
					"postcode":"${postcode}",
					"address":"${address}",
					"account":"${account}",
					"phone":"${phone}",
					"lname":"${jj.get("name").asText}",
					"code":"${code}",
					"status":1,
					"line_img":"${jj.get("img").asText}",
					"cts":${System.currentTimeMillis},
					"mark":""
				}"""
				redis.hset(REDIS_KEY_GIFT_REPORT_BAG, key, result)
				Ok("")
			}
		}
	}

 	def reportSe(lgid : String, uid : String, postcode : String, address: String, account: String, phone : String, code : String) = Action.async {
 		val key = lgid + "_" + uid

		for {
			s1 <- redis.smembers(REDIS_KEY_GIFT_SE_ACHIVE + uid) //Future[Seq[R]]
			isE <- redis.hexists(REDIS_KEY_GIFT_REPORT_SE, key)
			code1 <- redis.hget(REDIS_KEY_GIFT_CODE_KEY, key)
			kk <- redis.hget(REDIS_KEY_GIFT_SE, lgid)
		} yield {
			val achived = s1.map { _.utf8String }.toList
			if(!achived.contains(lgid)) {
				Ok("err:1")
			} else if(isE) {
				Ok("err:2")
			} else if(postcode == "" || address == "" || account == "" || phone == "" ) {
				Ok("err:3")
			} else if(!code1.isDefined || (code1.isDefined && code1.get.utf8String != code)) {
				Ok("err:4")
			} else if(!kk.isDefined) {
				Ok("err:5")
			} else {
				val jj = mapper.readTree(kk.get.utf8String)

				val result = s"""{
					"id":"${key}",
					"uid":"${uid}",
					"postcode":"${postcode}",
					"address":"${address}",
					"account":"${account}",
					"phone":"${phone}",
					"lname":"${jj.get("name").asText}",
					"code":"${code}",
					"status":1,
					"line_img":"${jj.get("img").asText}",
					"cts":${System.currentTimeMillis},
					"mark":""
				}"""
				redis.hset(REDIS_KEY_GIFT_REPORT_SE, key, result)
				Ok("")
			}
		}
	}

}