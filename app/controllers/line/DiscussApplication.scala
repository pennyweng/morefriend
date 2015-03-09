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


object DiscussApplication extends Controller {
	implicit val system = Akka.system
	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	def addDiscussRoom(uid : String, rid: String, name: String, desc : String, num: String) = Action {
		Logger.info(s"addDiscussRoom uid:${uid}, name:${name}")

		val id = {
			if(rid == "")
				System.currentTimeMillis.toString
			else rid
		}
		val data = s"""{"id":"${id}", "name":"${JSONFormat.quoteString(name)}", 
		"desc":"${JSONFormat.quoteString(desc)}", "num":"${num}"}"""
		redis.hset(REDIS_KEY_DISCUSS_ROOM, id, data)

		Ok("ok")			
	}

	def getAllDiscussRooms(uid : String) = Action.async {
		Logger.info(s"getAllDiscussRooms uid:${uid}")
		updateOnline(uid)
		updateActive(uid)

		redis.hgetall(REDIS_KEY_DISCUSS_ROOM).map { all => //Future[Map[String, R]]
			Ok(all.map { e => e._2.utf8String }.toArray.mkString("[", ",", "]"))
		}
	}



}
