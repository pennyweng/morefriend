package models

import dispatch._, Defaults._
import scala.xml._
import scala.collection.mutable.HashMap
import scala.util.parsing.json.JSONFormat
import scala.collection.JavaConversions._
import redis.RedisServer
import redis.RedisClientPool
import redis.api.Limit
import redis.RedisClient
import akka.actor.{ActorSystem, Props}
import controllers.line.Constants._
import play.api.libs.concurrent.Akka
import play.api.Play.current

import org.jsoup.Jsoup
import org.jsoup.Connection.Method
import org.jsoup.select._
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager
import java.util.Calendar
import scala.xml._ 
import org.jsoup.safety.Whitelist
import org.jsoup.nodes.Document.OutputSettings
import org.apache.commons.lang._

import scala.concurrent._
import scala.concurrent.duration._

case class Comment(tag : String, uid : String, content : String, ts : String) {
	def toJson = {
		s"""{"tag":"${JSONFormat.quoteString(tag)}",
		"uid":"${JSONFormat.quoteString(uid)}",
		"content":"${JSONFormat.quoteString(content)}",
		"ts":"${JSONFormat.quoteString(ts)}"}"""
	}
}
case class PttItem( id: String, title : String, subtitle: String, author : String, ts : String, url:String, image : String, from :String, good : Int = 0, normal : Int = 0, bad : Int = 0, body : String = "", img_links : String = "", source_links : String= "", comments : Array[Comment]) {
	def toJson() = {
		s"""{"id":"${JSONFormat.quoteString(id)}", 
		"title":"${JSONFormat.quoteString(title)}",
		"subtitle":"${JSONFormat.quoteString(subtitle)}",
		"author":"${JSONFormat.quoteString(author)}",
		"ts":"${ts}",
		"url":"${JSONFormat.quoteString(url)}",
		"image":"${JSONFormat.quoteString(image)}",
		"from":"${from}",
		"good":${good},
		"normal":${normal},
		"bad":${bad},
		"body":"${JSONFormat.quoteString(body)}",
		"img_links":"${JSONFormat.quoteString(img_links)}",
		"source_links":"${JSONFormat.quoteString(source_links)}",
		"comment":${comments.map{ _.toJson}.mkString("[",",","]") }
		}"""
	}
}

object Ptt {
	implicit val system = Akka.system
	// val redis = RedisClient(REDIS_HOST, 6379)
	val redis1 = RedisServer(REDIS_HOST, 6379)
	val redis = RedisClientPool(List(redis1))

	val interval = current.configuration.getLong("crawler.interval")

	val pttDataIndex = new HashMap[String, Array[String]]()
	val pttData = new HashMap[String, HashMap[String, PttItem]]()

	val httpClient = new dispatch.Http {
		import com.ning.http.client._
		val builder = new AsyncHttpClientConfig.Builder()
		builder
			.setAllowPoolingConnection(true)
			.setRequestTimeoutInMs(40 * 1000)
			.setConnectionTimeoutInMs(40 * 1000)
            .build()
		override val client = new AsyncHttpClient(builder.build())
	}
	
	val MAX_ITEM = 80
	val META_SIZE = 120

	def parse() {
		enableSSLSocket()

		getPttFrom18("/bbs/Gossiping/index.html", "Gossiping")
		getPttFrom18("/bbs/Sex/index.html", "Sex")

		getPtt("http://rss.ptt.cc/BuyTogether.xml", "BuyTogether")
		getPtt("http://rss.ptt.cc/Boy-Girl.xml", "Boy-Girl") 
		getPtt("http://rss.ptt.cc/Beauty.xml", "Beauty")
		getPtt("http://rss.ptt.cc/joke.xml", "joke")		
		getPtt("http://rss.ptt.cc/movie.xml", "movie")
		getPtt("http://rss.ptt.cc/WomenTalk.xml", "WomenTalk")
		getPtt("http://rss.ptt.cc/NBA.xml", "NBA")
		getPtt("http://rss.ptt.cc/Stock.xml", "Stock")
		getPtt("http://rss.ptt.cc/e-shopping.xml", "e-shopping")
		getPtt("http://rss.ptt.cc/Japan_Travel.xml", "Japan_Travel")
		getPtt("http://rss.ptt.cc/LoL.xml", "LoL")
		getPtt("http://rss.ptt.cc/Stock.xml", "Stock")
		getPtt("http://rss.ptt.cc/Baseball.xml", "Baseball")
		getPtt("http://rss.ptt.cc/Tech_Job.xml", "Tech_Job")	
		getPtt("http://rss.ptt.cc/PuzzleDragon.xml", "PuzzleDragon")
		getPtt("http://rss.ptt.cc/C_Chat.xml", "C_Chat")	
		getPtt("http://rss.ptt.cc/ToS.xml", "ToS")	
		getPtt("http://rss.ptt.cc/MobileComm.xml", "MobileComm")
		getPtt("http://rss.ptt.cc/BabyMother.xml",  "BabyMother")
		getPtt("http://rss.ptt.cc/KR_Entertain.xml", "KR_Entertain")
		getPtt("http://rss.ptt.cc/marvel.xml", "marvel")
		getPtt("http://rss.ptt.cc/NBA_Film.xml", "NBA_Film")
		getPtt("http://rss.ptt.cc/StupidClown.xml", "StupidClown")
		getPtt("http://rss.ptt.cc/DIABLO.xml", "DIABLO")
		getPtt("http://rss.ptt.cc/Hearthstone.xml", "Hearthstone")
		getPtt("http://rss.ptt.cc/car.xml", "car")
		getPtt("http://rss.ptt.cc/marriage.xml", "marriage")
		getPtt("http://rss.ptt.cc/KanColle.xml", "KanColle")
		getPtt("http://rss.ptt.cc/ForeignEX.xml", "ForeignEX")
		getPtt("http://rss.ptt.cc/SENIORHIGH.xml", "SENIORHIGH")
		getPtt("http://rss.ptt.cc/MakeUp.xml", "MakeUp")
		getPtt("http://rss.ptt.cc/Option.xml", "Option")
		getPtt("http://rss.ptt.cc/AllTogether.xml", "AllTogether")

		getPttFrom18("/bbs/SportLottery/index.html", "SportLottery")
		getPttFrom18("/bbs/japanavgirls/index.html", "japanavgirls")

		getPtt("http://rss.ptt.cc/Tainan.xml", "Tainan")
		getPtt("http://rss.ptt.cc/gay.xml", "gay")
		getPtt("http://rss.ptt.cc/Lifeismoney.xml", "Lifeismoney")
		getPtt("http://rss.ptt.cc/Kaohsiung.xml", "Kaohsiung")
		getPtt("http://rss.ptt.cc/HatePolitics.xml", "HatePolitics")
		getPtt("http://rss.ptt.cc/GetMarry.xml", "GetMarry")	
		getPtt("http://rss.ptt.cc/MLB.xml", "MLB")
		getPtt("http://rss.ptt.cc/home-sale.xml", "home-sale")
		getPtt("http://rss.ptt.cc/Japandrama.xml", "Japandrama")
		getPtt("http://rss.ptt.cc/Elephants.xml", "Elephants")
		getPtt("http://rss.ptt.cc/KoreaDrama.xml", "KoreaDrama")
		getPtt("http://rss.ptt.cc/PlayStation.xml", "PlayStation")
		getPtt("http://rss.ptt.cc/EAseries.xml", "EAseries")
		getPtt("http://rss.ptt.cc/Salary.xml", "Salary")
		getPtt("http://rss.ptt.cc/Wanted.xml", "Wanted")
		getPtt("http://rss.ptt.cc/iPhone.xml", "iPhone")
		getPtt("http://rss.ptt.cc/FCBarcelona.xml", "FCBarcelona")
		getPtt("http://rss.ptt.cc/Food.xml", "Food")
		getPtt("http://rss.ptt.cc/PC_Shopping.xml", "PC_Shopping")
		getPtt("http://rss.ptt.cc/BeautySalon.xml", "BeautySalon")
		getPtt("http://rss.ptt.cc/MenTalk.xml", "MenTalk")
		getPtt("http://rss.ptt.cc/Steam.xml", "Steam")
		getPtt("http://rss.ptt.cc/Aviation.xml", "Aviation")
		
		getPtt("http://rss.ptt.cc/studyteacher.xml", "studyteacher")
		getPtt("http://rss.ptt.cc/MuscleBeach.xml", "MuscleBeach")
		getPtt("http://rss.ptt.cc/creditcard.xml", "creditcard")
		getPtt("http://rss.ptt.cc/TaichungBun.xml", "TaichungBun")
		getPtt("http://rss.ptt.cc/mobilesales.xml", "mobilesales")
		getPtt("http://rss.ptt.cc/Hsinchu.xml", "Hsinchu")
		getPtt("http://rss.ptt.cc/lesbian.xml", "lesbian")
		getPtt("http://rss.ptt.cc/Examination.xml", "Examination")
		// getPtt("http://rss.ptt.cc/8words.xml", "8words")			
	}


	def getPttFrom18(pttUrl : String, from :String) {
		val indexDB = "PTT_" + from.toUpperCase + "_INDEX"
		val dataDB = "PTT_" + from.toUpperCase + "_DATA"

    	val doc = get18Document(pttUrl).parse

		try {
			val s  = doc.select("div")
			val newIds = doc.select("div.r-ent > div.title > a[href]").flatMap { element =>
				if(element.text.indexOf("公告") == -1 && element.text.indexOf("水桶") == -1) {
					Some(element.absUrl("href"))
				} else None
			}.toList

			val bh = redis.get(indexDB)
			val keys = Await.result(bh, 2 seconds)

			val (allIds, delD, existIds) = if(keys.isDefined) {
				val kk = keys.get.utf8String.split(",")
				val result1 = (newIds ++ kk).distinct.take(MAX_ITEM)
				val del = (newIds ++ kk).distinct.drop(MAX_ITEM)

				(result1, del, kk)
			} else {
				(newIds, List[String](), Array[String]())
			}

			val nnIds = allIds diff existIds
			println(from + "==>allIds size:" + allIds.size + ",del size" + delD.size + ",new size:" + nnIds.size)

			val newItems = parsePtt(nnIds, from, true)
			val alreadyIds = allIds diff nnIds
			redis.set(indexDB, (newItems.map { _.id } ++ alreadyIds).mkString(","))
			
			newItems.map { pttItem =>
				println("save:" + pttItem.id + ",dataDB:" + dataDB)
				redis.hset(dataDB, pttItem.id, pttItem.toJson)
			}
			
			if(delD.size > 0){
				redis.hdel(dataDB, delD :_*)
			}


			// redis.get(indexDB).map { keys => // Future[Option[R]]
				// val allIds = if(keys.isDefined) {
				// 	val kk = keys.get.utf8String.split(",")
				// 	(newIds ++ kk).distinct.take(MAX_ITEM)
				// } else {
				// 	newIds
				// }

				// val allItems = parsePtt(allIds, from, true)
				// redis.del(dataDB)
				// redis.set(indexDB, allItems.map { _.id }.mkString(","))
				
				// allItems.map { pttItem =>
				// 	println("save:" + pttItem.id)
				// 	redis.hset(dataDB, pttItem.id, pttItem.toJson)
				// }
			// }	
		} catch {
			case e : Throwable => e.printStackTrace
		}
	}


	def getPtt(pttUrl : String,  from :String) {
		val indexDB = "PTT_" + from.toUpperCase + "_INDEX"
		val dataDB = "PTT_" + from.toUpperCase + "_DATA"
		try {
			val svc = url(pttUrl)
			val hh = httpClient(svc OK as.xml.Elem)
			val xml = Await.result(hh, 15 seconds)

		
			val newIds = (xml \\ "entry").flatMap { a =>
				val id = (a \ "id").text
				val title = (a \ "title").text
				if(title.indexOf("公告") == -1 && title.indexOf("水桶") == -1) {
					Some(id)
				} else None
			}

			val bh = redis.get(indexDB)
			val keys = Await.result(bh, 2 seconds)
			// redis.get(indexDB).map { keys => // Future[Option[R]]
			val (allIds, delD, existIds) = if(keys.isDefined) {
				val kk = keys.get.utf8String.split(",")
				val result1 = (newIds ++ kk).distinct.take(MAX_ITEM)
				val del = (newIds ++ kk).distinct.drop(MAX_ITEM)
				(result1, del, kk)
			} else {
				(newIds, List[String](), Array[String]())
			}

			val nnIds = allIds diff existIds
			println(from + "==>allIds size:" + allIds.size + ",del size" + delD.size + ",new size:" + nnIds.size)
			
			val newItems = parsePtt(nnIds, from)
			val alreadyIds = allIds diff nnIds
			redis.set(indexDB, (newItems.map { _.id } ++ alreadyIds).mkString(","))
			
			newItems.map { pttItem =>
				println("save:" + pttItem.id + ",dataDB:" + dataDB)
				redis.hset(dataDB, pttItem.id, pttItem.toJson)
			}

			if(delD.size > 0) {
				redis.hdel(dataDB, delD :_*)
			}
			// }
		} catch {
			case e : Throwable => e.printStackTrace
		}	

	}


	def get18Document(newUrl : String) = {
		val nnurl = if(newUrl.indexOf("https://www.ptt.cc") != -1) newUrl.replaceAll("https://www.ptt.cc", "") else newUrl
		Jsoup.connect("https://www.ptt.cc/ask/over18")
		.data("from", nnurl, "yes", "yes")
		.method(Method.POST)
		.execute()
	}

	def getDocument(newUrl : String) = {
		Jsoup.connect(newUrl)
		.method(Method.GET)
		.execute()
	}


	def parsePtt(allIds : scala.collection.immutable.Seq[String], from : String, is18 : Boolean = false) =  {
		allIds.flatMap { id =>
			val newUrl = id.replaceAll("http://", "https://")
			try {
				Thread.sleep(interval.getOrElse(interval.get))
				
				val resp = {
					if(is18) get18Document(newUrl)
					else getDocument(newUrl)
				}

				val doc = resp.parse
				val good = doc.select("span.hl.push-tag").filter { e => e.text.indexOf("推") != -1}.size
				val normal = doc.select("span.hl.push-tag").filter { e => e.text.indexOf("→") != -1}.size
				val bad = doc.select("span.hl.push-tag").filter { e => e.text.indexOf("噓") != -1}.size
				val metadata = doc.select("div.article-metaline > span.article-meta-value")

				val tt = {
					val tt1 = doc.select("span.push-ipdatetime")
					val date1 = if(tt1.size > 0) {
						val sd2 = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.ENGLISH)
						val ss1 = tt1.last.text.split(" ").filter( _.indexOf(".") == -1).mkString(" ")
						sd2.parse(Calendar.getInstance().get(Calendar.YEAR) + "/" + ss1)
					} else if(metadata.eq(2).text != "") {
						val sd = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", java.util.Locale.ENGLISH)
						sd.parse(metadata.eq(2).text)
					} else null

					if(date1 != null) {
						val sd1 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ENGLISH)
						sd1.format(date1)
					} else ""
				}

				val (author, title) = {
					(metadata.eq(0).text, metadata.eq(1).text)	
				}

				val bodyHtml = getBodyHtml(resp.body)
				val sourceLinks =  doc.select("div#main-content > a[href]").map { ss1 => ss1.absUrl("href") }.mkString(",")
				val imgAllLinks = doc.select("img").map { ss1 => ss1.absUrl("src") }

				val imageUrl = {
					if(imgAllLinks.size > 0 && bodyHtml.indexOf(imgAllLinks(0).replaceAll("https:","").replaceAll("http:","")) != -1)
					imgAllLinks(0) else ""
				}
				val imgLinks = imgAllLinks.mkString(",")
				
				val comments = doc.select(".push").flatMap { dd =>
					if(dd.getElementsByClass("push-content") == null || dd.getElementsByClass("push-content").first == null) {
						None
					} else {
						val content = {
							val tmp = dd.getElementsByClass("push-content").first.text
							val cc = if(tmp.indexOf(": ") != -1) tmp.substring(2) else tmp
							cc
						}
						Some(Comment(dd.getElementsByClass("push-tag").first.text, 
							dd.getElementsByClass("push-userid").first.text, 
							content, 
							dd.getElementsByClass("push-ipdatetime").first.text))
					}
				}.toArray

				// println("bodyhtml::" + bodyHtml)
				val context = Jsoup.clean(bodyHtml, "", Whitelist.none(), new OutputSettings().prettyPrint(false))
				// println("context:" + context)

				val metaContext = {
					val ncount = StringUtils.countMatches(context, "\n")
					println("title" + title + ",ncount:" + ncount)
					if(ncount > 6) {
						context.substring(0, StringUtils.ordinalIndexOf(context, "\n", 6))
					} else {
						context.take(META_SIZE)
					}
				}

				if(title != "" && tt != "") {
					Some(PttItem(id, title, 
						org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4(metaContext),
						author, tt, newUrl, imageUrl, 
						from, good, normal, bad, 
						org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4(context),
						imgLinks, sourceLinks, comments))
				} else {
					None
				}
			} catch {
				case e : Throwable =>
					println(e.getMessage)
					// e.printStackTrace
					None
			}											
		}
	}

	def getBodyHtml(html : String) = {
		val beginHtml = """<!DOCTYPE html><html><head><meta charset="utf-8" />
		</head><body><pre>"""
		val endHtml = """</pre></body></html>"""

		// println("html:" + html)
		val startString = " "+ Calendar.getInstance().get(Calendar.YEAR) + """</span></div>"""
		val startIndex = html.indexOf(startString)

		val signString = """--"""
		val signStartIndex = html.lastIndexOf(signString) + 2

		if(startIndex != -1 && signStartIndex != -1 && signStartIndex > startIndex) {
			beginHtml + html.substring(startIndex + startString.length, signStartIndex).replaceAll("span","div") + endHtml
		} else ""
	} 

	def enableSSLSocket() {
		import javax.net.ssl._
		import java.io.IOException
		import java.security.KeyManagementException
		import java.security.NoSuchAlgorithmException
		import java.security.SecureRandom
		import java.security.cert.CertificateException
		import java.security.cert.X509Certificate

        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            def verify( hostname : String, session : SSLSession) = {
                true
            }
        });
 
        val context = SSLContext.getInstance("TLS");
        context.init(null, Array(new X509TrustManager() {
            def checkClientTrusted(chain : Array[X509Certificate], authType : String) {
            }
 
            def checkServerTrusted(chain : Array[X509Certificate], authType : String) {
            }
 
            def getAcceptedIssuers() = {
                new Array[X509Certificate](0)
            }
        }), new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory())
    }
}