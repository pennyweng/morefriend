package controllers.line.ad

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

object ADCallback extends Controller {
	implicit val system = Akka.system

	val redis = controllers.line.GiftApplication.redis

	def reportTrialpay() = Action { request =>
		Logger.info(s"reportTrialpay")
		val body = request.body
		println("body:" + body)
		Ok(body.toString)
	}

}