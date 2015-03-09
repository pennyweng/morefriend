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



object GameApplication3 extends Controller {
	// implicit val system = Akka.system

	// val NEXT_TIME = 3600000 * 5l
	// val GUESS_NUMBER = 5
	// val INIT_BIG = 100
	// val INIT_SMALL = 1

	// var isBOFinish = false

	// var currentBOBigNumber = INIT_BIG	
	// var currentBOSmalBOumber = INIT_SMALL	
	// var currentBOSNumber = GUESS_NUMBER	
	// var currentBOWinNumber = ""
	// var currentBOGameSt = 0l
	// var currentBOGid = ""
	// var currentBOGame = ""

	// val GAME_TYPE_BO = "bo"
	// val GAME_BO_NAME = s"${GUESS_NUMBER}x${GUESS_NUMBER}賓果"
	// val GAME_BO_INTRO = s"從${currentBOSmalBOumber}到${currentBOBigNumber}數字中，挑選一個數字。如果數字有出現在賓果卡，就會秀出上面，連成五條遊戲結束。看最後誰連成第五條線者，誰就獲勝" //。目前得獎機率(1/${currentBOBigNumber})
	// val GAME_BO_REWARD = """獲勝者可獲得7-11的50元商品卡。"""

	// val REDIS_KEY_GAME_BO_CURRENT = "GBOC"
	// val REDIS_KEY_GAME_BO_BIG = "GBOB"
	// val REDIS_KEY_GAME_BO_SMALL = "GBOS"
	// val REDIS_KEY_GAME_BO_START_TIME = "GBOST"
	// val REDIS_KEY_GAME_BO_COUNT = "GBOCU"
	// val REDIS_KEY_GAME_BO_FINISH = "GBOF"
	// val redis1 = RedisServer(REDIS_HOST, 6379)
	// val redis = RedisClientPool(List(redis1))
	

	// def resetBOGame(uid : String) = Action {
	// 	Logger.info(s"resetBOGame uid:${uid}")
	// 	Ok(createBOGame())	
	// }

	// def createBOGame() = {
	// 	Logger.info(s"createBOGame")
	// 	isBOFinish = false

	// 	// val ss = (1 to currentBOBigNumber).map { e => ( new java.util.Random().nextInt(1000), e)}.toArray
	// 	// scala.util.Sorting.quickSort(ss)
	// 	currentBOWinNumber = new java.util.Random().nextInt(currentBOBigNumber).toString//ss.take(6).map{ _._2 }.mkString(",")
	// 	currentBOGid = java.util.UUID.randomUUID.toString
	// 	currentBOGameSt = System.currentTimeMillis
	// 	currentBOGame = s"""{"gid":"${currentBOGid}", 
	// 	"name":"${GAME_BO_NAME}",
	// 	"st":${currentBOGameSt}, 
	// 	"gtype":"${GAME_TYPE_BO}",
	// 	"et":0,
	// 	"introduction":"${GAME_BO_INTRO}", 
	// 	"reward":"${GAME_BO_REWARD}", 
	// 	"is_finish":false,
	// 	"has_win":false,
	// 	"winner":null}"""

	// 	currentBOSNumber = GUESS_NUMBER
	// 	currentBOBigNumber = INIT_BIG	
	// 	currentBOSmalBOumber = INIT_SMALL	

	// 	redis.set(REDIS_KEY_GAME_BO_CURRENT, currentBOGid)	
	// 	redis.set(REDIS_KEY_GAME_BO_BIG, currentBOBigNumber)
	// 	redis.set(REDIS_KEY_GAME_BO_SMALL, currentBOSmalBOumber)
	// 	redis.set(REDIS_KEY_GAME_BO_START_TIME, currentBOGameSt)
	// 	redis.set(REDIS_KEY_GAME_BO_COUNT, currentBOSNumber)
		
	// 	redis.hset(REDIS_KEY_GAME_WIN_NUMBER, currentBOGid, currentBOWinNumber)
	// 	redis.hset(REDIS_KEY_GAME_TOTALS, currentBOGid, currentBOGame)
	// 	currentBOGame
	// }

	// def playBOGame(gid : String, uid : String, desc : String, lid : String) = Action.async {
	// 	Logger.info(s"current gid:${gid}, uid:${uid}, desc:${desc},  lid:${lid}")
		
	// 	val guess = new java.util.Random().nextInt(currentBOBigNumber).toString
	// 	val now = System.currentTimeMillis
	// 	redis.hget(REDIS_KEY_GAME_CHECK_TIME + gid, uid).map { tt =>
	// 		if(currentBOGid != gid) {
	// 			Logger.info(s"current gid:${currentBOGid}, your gid ${gid}")
	// 			Ok("err:1")
	// 		} else if(isBOFinish) {	
	// 			Logger.info(s"current gid:${isBOFinish} finish")
	// 			Ok("err:2")
	// 		} 
	// 		else if(tt.isDefined && now - tt.get.utf8String.toLong < NEXT_TIME) {
	// 			Logger.info(s"time is not over,current gid:${currentBOGid}, now ${now}, tt ${tt.get.utf8String.toLong}")
	// 			val sss = NEXT_TIME - (now - tt.get.utf8String.toLong)
	// 			Ok("err:3:" + sss)
	// 		} else {
	// 			if(guess == currentBOWinNumber) {
	// 				Logger.info(s"you guess the number, gid ${gid}")
	// 				redis.hset(REDIS_KEY_GAME_HISTORY + gid, now.toString , uid + "##戳到大獎##" + desc)

	// 				isBOFinish = true
	// 				redis.hset(REDIS_KEY_GAME_WINER, gid, uid)
	// 				redis.hlen(REDIS_KEY_GAME_HISTORY + gid).map { ae =>
	// 					currentBOGame = s"""{"game_info":{"gid":"${gid}", 
	// 								"name":"${GAME_BO_NAME}",
	// 								"st":${currentBOGameSt}, 
	// 								"gtype":"${GAME_TYPE_BO}",
	// 								"et":${System.currentTimeMillis},
	// 								"introduction":"${GAME_BO_INTRO}", 
	// 								"reward":"${GAME_BO_REWARD}", 
	// 								"is_finish":true,
	// 								"has_win":true,
	// 								"total":${ae},
	// 								"winner_n":"${guess}",
	// 								"winner_lid":"${lid}",
	// 								"winner":"${uid}"}}"""
	// 					redis.hset(REDIS_KEY_GAME_BO_FINISH, gid, currentBOGame)
	// 					redis.hset(REDIS_KEY_GAME_TOTALS, gid, currentBOGame)
	// 					redis.hset(REDIS_KEY_GAME_CHECK_TIME + gid, uid, now)
	// 					createBOGame()						
	// 				}

	// 				Ok(s"""${currentBOSNumber}##${0}##winner""")
	// 			} else { 
	// 				redis.hset(REDIS_KEY_GAME_HISTORY + gid, now.toString , uid + "##銘謝惠顧##" + desc)
	// 				redis.hset(REDIS_KEY_GAME_CHECK_TIME + gid, uid, now)
	// 				Logger.info(s"""current BO game currentBOGid:${currentBOGid},
	// 					currentBOWinNumber:${currentBOWinNumber},
	// 					currentBOBigNumber:${currentBOBigNumber}, 
	// 					currentBOSmalBOumber:${currentBOSmalBOumber}, 
	// 					isBOFinish:${isBOFinish},
	// 					currentBOGame:${currentBOGame},
	// 					currentBOGameSt:${currentBOGameSt} """)

	// 				Ok(s"""${guess}""")
	// 			}	
	// 		} 
	// 	}
	// }

	// def reloadCurrentBOGame() {
	// 	Logger.info(s"run reloadCurrentGuessGame")
	// 	for{
	// 		n1 <- redis.get(REDIS_KEY_GAME_BO_BIG) // Future[Option[R]]
	// 		n2 <- redis.get(REDIS_KEY_GAME_BO_SMALL)
	// 		n3 <- redis.get(REDIS_KEY_GAME_BO_CURRENT)
	// 		n4 <- redis.get(REDIS_KEY_GAME_BO_START_TIME)
	// 		n8 <- redis.get(REDIS_KEY_GAME_BO_COUNT)
	// 		n5 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_WIN_NUMBER, n3.get.utf8String) else Future.successful(None)
	// 		n6 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_TOTALS, n3.get.utf8String) else Future.successful(None)
	// 		n7 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_WINER, n3.get.utf8String) else Future.successful(None)
	// 	} {
	// 		if(n3.isDefined) currentBOGid = n3.get.utf8String
	// 		if(n5.isDefined) currentBOWinNumber = n5.get.utf8String
	// 		if(n1.isDefined) currentBOBigNumber = n1.get.utf8String.toInt
	// 		if(n2.isDefined) currentBOSmalBOumber = n2.get.utf8String.toInt
	// 		if(n7.isDefined) isBOFinish = true else isBOFinish = false
	// 		if(n6.isDefined) currentBOGame = n6.get.utf8String
	// 		if(n4.isDefined) currentBOGameSt = n4.get.utf8String.toLong
	// 		if(n8.isDefined) currentBOSNumber = n8.get.utf8String.toInt
	// 		Logger.info(s"""current BO game currentBOGid:${currentBOGid},
	// 			currentBOWinNumber:${currentBOWinNumber},
	// 			currentBOBigNumber:${currentBOBigNumber}, 
	// 			currentBOSmalBOumber:${currentBOSmalBOumber}, 
	// 			isBOFinish:${isBOFinish},
	// 			currentBOGame:${currentBOGame},
	// 			currentBOGameSt:${currentBOGameSt} """)		
	// 	}
	// }

	// def getAllFinishGames( uid :String) = Action.async {
	// 	val guess = redis.hgetall(REDIS_KEY_GAME_GUESS_FINISH).map { all => //Future[Map[String, R]]
	// 		all.map { k => 
	// 				val jj = mapper.readTree(k._2.utf8String)
	// 				if(jj.get("game_info").get("et") != null)
	// 					(jj.get("game_info").get("et").asText, k._2.utf8String)
	// 				else ("0", k._2.utf8String)
	// 		}.toArray
	// 	}

	// 	val ab = redis.hgetall(GameApplication1.REDIS_KEY_GAME_AB_FINISH).map { all => //Future[Map[String, R]]
	// 		all.map { k => 
	// 				val jj = mapper.readTree(k._2.utf8String)
	// 				if(jj.get("game_info").get("et") != null)
	// 					(jj.get("game_info").get("et").asText, k._2.utf8String)
	// 				else ("0", k._2.utf8String)
	// 			}.toArray
	// 	}

	// 	val nl = redis.hgetall(GameApplication2.REDIS_KEY_GAME_BO_FINISH).map { all => //Future[Map[String, R]]
	// 		all.map { k => 
	// 				val jj = mapper.readTree(k._2.utf8String)
	// 				if(jj.get("game_info").get("et") != null)
	// 					(jj.get("game_info").get("et").asText, k._2.utf8String)
	// 				else ("0", k._2.utf8String)
	// 			}.toArray
	// 	}

	// 	for {
	// 		aa <- guess
	// 		bb <- ab
	// 		cc <- nl
	// 	} yield {
	// 		val ee = aa ++ bb ++ cc
	// 		scala.util.Sorting.quickSort(ee)
	// 		val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
	// 		Ok(ooo)			
	// 	}
	// }

	// def getCurrentAllGame(uid : String) = Action.async {
	// 	Logger.info(s"getCurrentAllGame uid:${uid}")

	// 	val guessGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication.currentGuessGid).map { ee =>
	// 		s"""{"game_info":${GameApplication.currentGuessGame},"small":${GameApplication.currentGuessSmalBOumber}, 
	// 		"big":${GameApplication.currentGuessBigNumber},"count":${ee} }"""
	// 	}

	// 	val abGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication1.currentABGid).map { ee =>
	// 		s"""{"game_info":${GameApplication1.currentABGame},"small":${GameApplication1.currentABSmalBOumber}, 
	// 		"big":${GameApplication1.currentABBigNumber},"count":${ee},"number":${GameApplication1.currentABSNumber} }"""
	// 	}	

	// 	val BOGame = redis.hlen(REDIS_KEY_GAME_HISTORY + GameApplication2.currentBOGid).map { ee =>
	// 		s"""{"game_info":${currentBOGame},"small":${GameApplication2.currentBOSmalBOumber}, 
	// 		"big":${GameApplication2.currentBOBigNumber},"count":${ee},"number":${GameApplication2.currentBOSNumber} }"""
	// 	}	

	// 	for {
	// 		a <- guessGame
	// 		b <- abGame
	// 		c <- BOGame
	// 	} yield {
	// 		Ok(s"[${a},${b},${c}]")	
	// 	}
	// }
}
