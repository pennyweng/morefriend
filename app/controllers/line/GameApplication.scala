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
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}

object  GameApplication extends Controller {
	implicit val system = Akka.system

	val queue = new LinkedBlockingQueue[String]()
	val mapper = new ObjectMapper()
	mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
		
	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

//=======================================
	val GAME_TYPE_GUESS = "tg"
	val GAME_TYPE_FIVE = "tf"


	val GAME_GUESS_NAME = "終極密碼"
	val GAME_GUESS_INTRO = """系統會自動選定一個範圍的數字，讓其餘的人猜數字。如果沒猜中的話，系統要會依猜出的數字將範圍縮小，讓下一個輪到的人繼續猜，直到猜中數字為止。猜中這個數字的人就獲勝。"""
	val GAME_GUESS_REWARD = """獲勝者可獲得7-11的50元商品卡。"""
	// val GAME_GUESS_START = "0-10000"

	val GAME_FIVE_NAME = "510樓"
	val GAME_FIVE_INTRO = """參加者每次最多將樓層向前推進3樓，最少向前推進1樓。例如，前一個人已經將樓層推近到31樓，下一個人最多就可以到34樓。看誰是最後進到510樓的人就是獲勝。"""
	val GAME_FIVE_REWARD = """獲勝者可獲得7-11的50元商品卡。"""
	val GAME_FIVE_START = "0"
	val INIT_BIG_NUMBER = 888888888l
	val NEXT_TIME = 3600000 * 5l

	var currentGuessGid = ""
	var currentGuessWinNumber = -1l
	var currentGuessBigNumber = INIT_BIG_NUMBER
	var currentGuessSmallNumber = 0l
	var isGuessFinish = false
	var currentGuessGame = ""
	var currentGuessGameSt = 0l

	def getNextTime(uid :String, gid : String) = Action.async {
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_GAME_CHECK_TIME + gid, uid).map { tt =>
			if(tt.isDefined && now - tt.get.utf8String.toLong < NEXT_TIME) {
				Logger.info(s"time is not over,current gid:${currentGuessGid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = NEXT_TIME - (now - tt.get.utf8String.toLong)
				Ok(sss + "")
			} else Ok("0")
		}
	}

	def getGuessFinishGames( uid :String) = Action.async {
		redis.hgetall(REDIS_KEY_GAME_GUESS_FINISH).map { all => //Future[Map[String, R]]
			val ee = all.map { k => 
					val jj = mapper.readTree(k._2.utf8String)
					if(jj.get("game_info").get("et") != null)
						(jj.get("game_info").get("et").asText, k._2.utf8String)
					else ("0", k._2.utf8String)
				}.toArray
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			Ok(ooo)
			// Ok(all.map { e => e._2.utf8String }.toArray.mkString("[", ",", "]"))
		}
	}

	// //67-899
	def playGuessGame(gid : String, uid : String, guess : String, desc : String, lid : String) = Action.async {
		Logger.info(s"current gid:${gid}, uid:${uid}, guess:${guess}, desc:${desc},  lid:${lid}")
		
		val now = System.currentTimeMillis
		redis.hget(REDIS_KEY_GAME_CHECK_TIME + gid, uid).map { tt =>
			if(currentGuessGid != gid) {
				Logger.info(s"current gid:${currentGuessGid}, your gid ${gid}")
				Ok("err:1")
			} else if(isGuessFinish) {	
				Logger.info(s"current gid:${currentGuessGid} finish")
				Ok("err:2")
			} else if(tt.isDefined && now - tt.get.utf8String.toLong < NEXT_TIME) {
				Logger.info(s"time is not over,current gid:${currentGuessGid}, now ${now}, tt ${tt.get.utf8String.toLong}")
				val sss = NEXT_TIME - (now - tt.get.utf8String.toLong)
				Ok("err:3:" + sss)
			} else {
				if(guess.toLong == currentGuessWinNumber) {
					Logger.info(s"you guess the number, gid ${gid}")
					redis.hset(REDIS_KEY_GAME_HISTORY + gid, now.toString , uid + "##" + guess + "##" + desc)

					isGuessFinish = true
					redis.hset(REDIS_KEY_GAME_WINER, gid, uid)
					currentGuessSmallNumber = guess.toLong
					currentGuessBigNumber = guess.toLong
					redis.hlen(REDIS_KEY_GAME_HISTORY + gid).map { ae =>
						currentGuessGame = s"""{"game_info":{"gid":"${gid}", 
									"name":"${GAME_GUESS_NAME}",
									"st":${currentGuessGameSt}, 
									"gtype":"${GAME_TYPE_GUESS}",
									"et":${System.currentTimeMillis},
									"introduction":"${GAME_GUESS_INTRO}", 
									"reward":"${GAME_GUESS_REWARD}", 
									"is_finish":true,
									"has_win":true,
									"total":${ae},
									"winner_n":"${guess}",
									"winner_lid":"${lid}",
									"winner":"${uid}"}}"""
						redis.hset(REDIS_KEY_GAME_GUESS_FINISH, gid, currentGuessGame)
						redis.hset(REDIS_KEY_GAME_TOTALS, gid, currentGuessGame)
						redis.set(REDIS_KEY_GAME_GUESS_BIG, currentGuessBigNumber)
						redis.set(REDIS_KEY_GAME_GUESS_SMALL, currentGuessSmallNumber)
						redis.hset(REDIS_KEY_GAME_CHECK_TIME + gid, uid, now)
						rGuessGame()						
					}

					Ok(s"""${currentGuessSmallNumber}##${currentGuessBigNumber}##winner""")
				} else { 
					if(guess.toLong < currentGuessSmallNumber) {
						// don't do anything
						Logger.info(s"you guess the number too small, currentGuessSmallNumber:${currentGuessSmallNumber}")
					} else if(guess.toLong > currentGuessBigNumber) {
						// don't do anything
						Logger.info(s"you guess the number too large, currentGuessBigNumber:${currentGuessBigNumber}")
					} else {
						if(guess.toLong < currentGuessWinNumber) {
							currentGuessSmallNumber = guess.toLong
						} else if(guess.toLong > currentGuessWinNumber) {
							currentGuessBigNumber = guess.toLong
						} 
					}
					redis.set(REDIS_KEY_GAME_GUESS_BIG, currentGuessBigNumber)
					redis.set(REDIS_KEY_GAME_GUESS_SMALL, currentGuessSmallNumber)
					redis.hset(REDIS_KEY_GAME_HISTORY + gid, now.toString , uid + "##" + guess + "##" + desc)
					redis.hset(REDIS_KEY_GAME_CHECK_TIME + gid, uid, now)
					Logger.info(s"""current guess game currentGuessGid:${currentGuessGid},
						currentGuessWinNumber:${currentGuessWinNumber},
						currentGuessBigNumber:${currentGuessBigNumber}, 
						currentGuessSmallNumber:${currentGuessSmallNumber}, 
						isGuessFinish:${isGuessFinish},
						currentGuessGame:${currentGuessGame},
						currentGuessGameSt:${currentGuessGameSt} """)

					Ok(s"""${currentGuessSmallNumber}##${currentGuessBigNumber}""")
				}	
			} 
		}
	}

	def getGuessGameHistory(uid : String, gid : String) = Action.async {
		Logger.info(s"getGameHistory uid:${uid}, gid:${gid}")
		redis.hgetall(REDIS_KEY_GAME_HISTORY + gid).map { e => //Future[Map[String, R]]
			val ee = e.map { k => (k._1, s"""{"ts":${k._1},"h":"${k._2.utf8String}"}""")}.toArray
			scala.util.Sorting.quickSort(ee)
			val ooo = ee.reverse.map { k => k._2 }.mkString("[",",","]")
			// val ooo = e.map { k => s"""{"ts":${k._1},"h":"${k._2.utf8String}"}"""}.mkString("[",",","]")
			
			Ok(ooo)
		}
	}

	def getCurrentGuessGame(uid : String) = Action.async {
		Logger.info(s"getCurrentGuessGame uid:${uid}")
		redis.hlen(REDIS_KEY_GAME_HISTORY + currentGuessGid).map { ee =>
			val a1 = s"""{"game_info":${currentGuessGame},"small":${currentGuessSmallNumber}, 
			"big":${currentGuessBigNumber},"count":${ee} }"""
			Ok("[" + a1 + "]")			
		}

	}

	def reloadCurrentGuessGame() {
		Logger.info(s"run reloadCurrentGuessGame")
		for{
			n1 <- redis.get(REDIS_KEY_GAME_GUESS_BIG) // Future[Option[R]]
			n2 <- redis.get(REDIS_KEY_GAME_GUESS_SMALL)
			n3 <- redis.get(REDIS_KEY_GAME_GUESS_CURRENT)
			n4 <- redis.get(REDIS_KEY_GAME_GUESS_START_TIME)
			n5 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_WIN_NUMBER, n3.get.utf8String) else Future.successful(None)
			n6 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_TOTALS, n3.get.utf8String) else Future.successful(None)
			n7 <- if(n3.isDefined) redis.hget(REDIS_KEY_GAME_WINER, n3.get.utf8String) else Future.successful(None)
		} {
			if(n3.isDefined) currentGuessGid = n3.get.utf8String
			if(n5.isDefined) currentGuessWinNumber = n5.get.utf8String.toLong
			if(n1.isDefined) currentGuessBigNumber = n1.get.utf8String.toLong
			if(n2.isDefined) currentGuessSmallNumber = n2.get.utf8String.toLong
			if(n7.isDefined) isGuessFinish = true else isGuessFinish = false
			if(n6.isDefined) currentGuessGame = n6.get.utf8String
			if(n4.isDefined) currentGuessGameSt = n4.get.utf8String.toLong	
			Logger.info(s"""current guess game currentGuessGid:${currentGuessGid},
				currentGuessWinNumber:${currentGuessWinNumber},
				currentGuessBigNumber:${currentGuessBigNumber}, 
				currentGuessSmallNumber:${currentGuessSmallNumber}, 
				isGuessFinish:${isGuessFinish},
				currentGuessGame:${currentGuessGame},
				currentGuessGameSt:${currentGuessGameSt} """)		
		}
	}

	def rGuessGame() : String= {
		Logger.info(s"resetGuessGame")

		isGuessFinish = false
		currentGuessGame = ""
		// currentGuessGid = java.util.UUID.randomUUID.toString
		// currentGuessWinNumber = {
		// 	val max = INIT_BIG_NUMBER
		// 	new java.util.Random().nextInt(max.toInt)
		// }
		// currentGuessGameSt = System.currentTimeMillis
		// currentGuessBigNumber = INIT_BIG_NUMBER
		// currentGuessSmallNumber = 0
		// currentGuessGame = s"""{"gid":"${currentGuessGid}", 
		// "name":"${GAME_GUESS_NAME}",
		// "st":${currentGuessGameSt}, 
		// "gtype":"${GAME_TYPE_GUESS}",
		// "et":0,
		// "introduction":"${GAME_GUESS_INTRO}", 
		// "reward":"${GAME_GUESS_REWARD}", 
		// "is_finish":false,
		// "has_win":false,
		// "winner":null}"""

		// redis.set(REDIS_KEY_GAME_GUESS_BIG, currentGuessBigNumber)
		// redis.set(REDIS_KEY_GAME_GUESS_SMALL, currentGuessSmallNumber)
		// redis.set(REDIS_KEY_GAME_GUESS_CURRENT, currentGuessGid)
		// redis.set(REDIS_KEY_GAME_GUESS_START_TIME, currentGuessGameSt)
		// redis.hset(REDIS_KEY_GAME_WIN_NUMBER, currentGuessGid, currentGuessWinNumber)		
		// redis.hset(REDIS_KEY_GAME_TOTALS, currentGuessGid, currentGuessGame)

		// Logger.info(s"""current guess game currentGuessGid:${currentGuessGid},
		// 	currentGuessWinNumber:${currentGuessWinNumber},
		// 	currentGuessBigNumber:${currentGuessBigNumber}, 
		// 	currentGuessSmallNumber:${currentGuessSmallNumber}, 
		// 	isGuessFinish:${isGuessFinish},
		// 	currentGuessGame:${currentGuessGame},
		// 	currentGuessGameSt:${currentGuessGameSt} """)	
		currentGuessGame			
	}

	def rGuessGame1() : String= {
		Logger.info(s"resetGuessGame")

		isGuessFinish = false
		currentGuessGid = java.util.UUID.randomUUID.toString
		currentGuessWinNumber = {
			val max = INIT_BIG_NUMBER
			new java.util.Random().nextInt(max.toInt)
		}
		currentGuessGameSt = System.currentTimeMillis
		currentGuessBigNumber = INIT_BIG_NUMBER
		currentGuessSmallNumber = 0
		currentGuessGame = s"""{"gid":"${currentGuessGid}", 
		"name":"${GAME_GUESS_NAME}",
		"st":${currentGuessGameSt}, 
		"gtype":"${GAME_TYPE_GUESS}",
		"et":0,
		"introduction":"${GAME_GUESS_INTRO}", 
		"reward":"${GAME_GUESS_REWARD}", 
		"is_finish":false,
		"has_win":false,
		"winner":null}"""

		redis.set(REDIS_KEY_GAME_GUESS_BIG, currentGuessBigNumber)
		redis.set(REDIS_KEY_GAME_GUESS_SMALL, currentGuessSmallNumber)
		redis.set(REDIS_KEY_GAME_GUESS_CURRENT, currentGuessGid)
		redis.set(REDIS_KEY_GAME_GUESS_START_TIME, currentGuessGameSt)
		redis.hset(REDIS_KEY_GAME_WIN_NUMBER, currentGuessGid, currentGuessWinNumber)		
		redis.hset(REDIS_KEY_GAME_TOTALS, currentGuessGid, currentGuessGame)

		Logger.info(s"""current guess game currentGuessGid:${currentGuessGid},
			currentGuessWinNumber:${currentGuessWinNumber},
			currentGuessBigNumber:${currentGuessBigNumber}, 
			currentGuessSmallNumber:${currentGuessSmallNumber}, 
			isGuessFinish:${isGuessFinish},
			currentGuessGame:${currentGuessGame},
			currentGuessGameSt:${currentGuessGameSt} """)	
		currentGuessGame			
	}

	def resetGuessGame(uid : String) = Action {
		Logger.info(s"resetGuessGame uid:${uid}")
		Ok(rGuessGame())	
	}

	// def requestNewGame(uid : String, gtype : String) = Action {
	// 	val gid = java.util.UUID.randomUUID.toString
	// 	if(gtype == GAME_TYPE_GUESS) {
	// 		val winNumber = {
	// 			val max = GAME_GUESS_START.split("-")(1).toInt
	// 			new java.util.Random().nextInt(max)
	// 		}
	// 		redis.hset(REDIS_KEY_GAME_WIN_NUMBER, gid, winNumber)

	// 		val rr = s"""{"gid":"${gid}" 
	// 		"name":"${GAME_GUESS_NAME}",
	// 		"st":${System.currentTimeMillis}, 
	// 		"gtype":"${gtype}",
	// 		"et":0, 
	// 		"count":0, 
	// 		"introduction":"${GAME_GUESS_INTRO}", 
	// 		"reward":"${GAME_GUESS_REWARD}", 
	// 		"current_number":"${GAME_GUESS_START}", 
	// 		"is_finish":false,
	// 		"has_win":false,
	// 		"winner":null}"""
	// 		redis.hset(REDIS_KEY_GAME_TOTALS, gid, rr)
	// 	} else if(gtype == GAME_TYPE_FIVE) {
	// 		val rr = s"""{"gid":"${gid}" 
	// 		"name":"${GAME_FIVE_NAME}",
	// 		"st":${System.currentTimeMillis}, 
	// 		"gtype":"${gtype}",
	// 		"et":0, 
	// 		"count":0, 
	// 		"introduction":"${GAME_FIVE_INTRO}", 
	// 		"reward":"${GAME_FIVE_REWARD}", 
	// 		"current_number":"${GAME_FIVE_START}", 
	// 		"is_finish":false,
	// 		"has_win":false,
	// 		"winner":null}"""
	// 		redis.hset(REDIS_KEY_GAME_TOTALS, gid, rr)

	// 	}
	// 	Ok("")
	// }
}

// // Abstract consumer
// abstract class Consumer[T](queue: BlockingQueue[T]) extends Runnable {
//   def run() {
//     while (true) {
//       val item = queue.take()
//       consume(item)
//     }
//   }

//   def consume(x: T)
// }

// class IndexerConsumer(index: InvertedIndex, queue: BlockingQueue[String]) extends Consumer[String](queue) {
//   def consume(t: String) = {

//   }
// }



