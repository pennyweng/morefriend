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
import controllers.line.Constants._
import controllers.line.TraceApplication._

object SearchApplication extends Controller {
	implicit val system = Akka.system
	val NUM_OF_PAGE = 50
	// val redis = RedisClient(REDIS_HOST, 6379)
	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	def searchCategoryCount(categoryType : String, s : String, uid : String) = Action.async {
		Logger.info(s"search searchCategoryCount categoryType:${categoryType}")
		updateOnline(uid)
		updateActive(uid)

		val prefixKeys = if(categoryType == "0") {
			if(s == "M")
				ALL_INTERESTS_ID.map { id => REDIS_KEY_INTEREST_M + id}.toList
			else if(s == "F")
				ALL_INTERESTS_ID.map { id => REDIS_KEY_INTEREST_F + id}.toList
			else 				
				ALL_INTERESTS_ID.map { id => REDIS_KEY_INTEREST + id}.toList
		} else if(categoryType == "1") {
			if(s == "M")
				ALL_PLACES_ID.map { id => REDIS_KEY_PLACE_M + id}.toList
			else if(s == "F")				
				ALL_PLACES_ID.map { id => REDIS_KEY_PLACE_F + id}.toList
			else				
				ALL_PLACES_ID.map { id => REDIS_KEY_PLACE + id}.toList
		} else if(categoryType == "2") {
			if(s == "M")
				ALL_CAREERS_ID.map { id => REDIS_KEY_CAREER_M + id}.toList
			else if( s== "F")
				ALL_CAREERS_ID.map { id => REDIS_KEY_CAREER_F + id}.toList
			else
				ALL_CAREERS_ID.map { id => REDIS_KEY_CAREER + id}.toList
		} else  if(categoryType == "3") {
			if(s == "M")
				ALL_OLDS_ID.map { id => REDIS_KEY_OLD_M + id}.toList
			else if(s == "F")
				ALL_OLDS_ID.map { id => REDIS_KEY_OLD_F + id}.toList
			else 
				ALL_OLDS_ID.map { id => REDIS_KEY_OLD + id}.toList
		} else  if(categoryType == "4") {
			if(s == "M")
				ALL_CONSTELLATION_ID.map { id => REDIS_KEY_CONSTELLATION_M + id}.toList
			else if(s == "F")
				ALL_CONSTELLATION_ID.map { id => REDIS_KEY_CONSTELLATION_F + id}.toList
			else 
				ALL_CONSTELLATION_ID.map { id => REDIS_KEY_CONSTELLATION + id}.toList
		} else {
			if(s == "M")
				ALL_MOTION_ID.map { id => REDIS_KEY_MOTION_M + id}.toList
			else if(s == "F")
				ALL_MOTION_ID.map { id => REDIS_KEY_MOTION_F + id}.toList
			else 
				ALL_MOTION_ID.map { id => REDIS_KEY_MOTION + id}.toList
		}

		val a = prefixKeys.map { key => redis.zcount(key).map { e => (key.substring(key.indexOf(":") + 1), e)  } }
		
		for {
			no <- redis.hget(REDIS_KEY_NOTIFICATION, uid) // Future[Option[R]]
			uu <- checkuser(no, uid)
			kk <- Future.sequence(a)
		} yield {
			val resp = kk.map { ee =>
				s"""{"id":${ee._1},"count":${ee._2}}"""
			}.mkString("[", ",","]")

			val resp1 = if(uid != "") {
				val nn = if(no.isDefined && uu) s"""{"command":"GET_NOTIFICATION","ts":"${no.get.utf8String}"} """  else null
				s"""{"r":${resp},"n":${nn}}"""
			} else resp
			Ok(resp1)
		}
	}

	def checkuser( nn : Option[akka.util.ByteString], uid : String ) = {
		if(nn.isDefined){
			val k = nn.get.utf8String
			if(k.indexOf("##") != -1) {
				val uid = k.split("##")(1)
				redis.hexists(REDIS_KEY_USER, uid) //Future[Boolean]
			} else {
				redis.hdel(REDIS_KEY_NOTIFICATION, uid)
				Future.successful(false)
			}
		} else {
			redis.hdel(REDIS_KEY_NOTIFICATION, uid)
			Future.successful(false)
		}
	}

	def search(categoryType : String, categoryId : String, page: Int, s : String, uid : String) = Action.async {
		Logger.info(s"search categoryType:${categoryType},categoryId:${categoryId},page:${page}")
		updateOnline(uid)
		updateActive(uid)
		
		// val start =  (0 - NUM_OF_PAGE) - (NUM_OF_PAGE * page)
		// val end = -1 - (NUM_OF_PAGE * page)

		val start =  (NUM_OF_PAGE * page)
		val end = (NUM_OF_PAGE * (page + 1)) - 1

		val redisKey = if(categoryType == "0") { 
			if(s == "M")
				REDIS_KEY_INTEREST_M + categoryId
			else if(s == "F")
				REDIS_KEY_INTEREST_F + categoryId
			else 
				REDIS_KEY_INTEREST + categoryId
		} else if(categoryType == "1") {
			if(s == "M")
				REDIS_KEY_PLACE_M + categoryId
			else if(s == "F")				
				REDIS_KEY_PLACE_F + categoryId
			else
				REDIS_KEY_PLACE + categoryId
		} else if(categoryType == "2")  {
			if(s == "M")
				REDIS_KEY_CAREER_M + categoryId
			else if( s== "F")
				REDIS_KEY_CAREER_F + categoryId
			else 
				REDIS_KEY_CAREER + categoryId
		} else if(categoryType == "3")  {
			if( s == "M")
				REDIS_KEY_OLD_M + categoryId
			else if( s == "F")
				REDIS_KEY_OLD_F + categoryId
			else 				
				REDIS_KEY_OLD + categoryId
		} else if(categoryType == "4")  {
			if( s == "M")
				REDIS_KEY_CONSTELLATION_M + categoryId
			else if( s == "F")
				REDIS_KEY_CONSTELLATION_F + categoryId
			else 				
				REDIS_KEY_CONSTELLATION + categoryId
		}  else  {
			if( s == "M")
				REDIS_KEY_MOTION_M + categoryId
			else if( s == "F")
				REDIS_KEY_MOTION_F + categoryId
			else 				
				REDIS_KEY_MOTION + categoryId
		}

		Logger.debug("redisKey:" + redisKey+ ",start:" + start + ",end:" + end)
		for {
			no <- redis.hget(REDIS_KEY_NOTIFICATION, uid) // Future[Option[R]]
			ut <- redis.zrevrange(redisKey, start, end) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_USER, ut.map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")

			val resp1 = if(uid != "") {
					val nn = if(no.isDefined) s"""{"command":"GET_NOTIFICATION","ts":${no.get.utf8String}}"""  else null
					s"""{"r":${resp},"n":${nn}}"""
				} else resp
			Ok(resp1)
		}
	}	


	def search1(page: Int, s : String, uid : String) = Action.async {
		Logger.info(s"search page:${page}")
		updateOnline(uid)
		updateActive(uid)
		
		// val start =  (0 - NUM_OF_PAGE) - (NUM_OF_PAGE * page)
		// val end = -1 - (NUM_OF_PAGE * page)

		val start =  (NUM_OF_PAGE * page)
		val end = (NUM_OF_PAGE * (page + 1)) - 1
		val redisKey =  if(s == "M") REDIS_KEY_ACCOUNT_MAN 
			else if(s == "F") REDIS_KEY_ACCOUNT_WOMEN
			else REDIS_KEY_ACCOUNT

		Logger.debug("redisKey:" + redisKey+ ",start:" + start + ",end:" + end)
		for {
			no <- redis.hget(REDIS_KEY_NOTIFICATION, uid) // Future[Option[R]]
			ut <- redis.zrevrange(redisKey, start, end) // //Future[Seq[R]]
			h <- if(ut.length > 0) redis.hmget(REDIS_KEY_USER, ut.map{ u => u.utf8String}.toList : _*) else Future.successful(List(None))//Future[Seq[Option[R]]]
		} yield {
			val resp = h.flatten.map { ee =>
				ee.utf8String
			}.mkString("[", ",", "]")

			val resp1 = if(uid != "") {
					val nn = if(no.isDefined) s"""{"command":"GET_NOTIFICATION","ts":${no.get.utf8String}}"""  else null
					s"""{"r":${resp},"n":${nn}}"""
				} else resp
			Ok(resp1)
		}
	}		
}
