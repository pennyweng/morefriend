package models

import dispatch._, Defaults._
import scala.xml._
import scala.collection.mutable.HashMap
import scala.util.parsing.json.JSONFormat
import scala.collection.JavaConversions._

case class PttItem( id: String, title : String, subtitle: String, author : String, ts : String, url:String, image : String) {
	def toJson() = {
		s"""{"id":"${JSONFormat.quoteString(id)}", 
		"title":"${JSONFormat.quoteString(title)}",
		"subtitle":"${JSONFormat.quoteString(subtitle)}",
		"author":"${JSONFormat.quoteString(author)}",
		"ts":${ts},
		"url":"${JSONFormat.quoteString(url)}",
		"image":"${JSONFormat.quoteString(image)}" }"""
	}
}

object Ptt {
	val KEY_BOY_GIRL = "boy-girl"
	val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
	val pttDataIndex = new HashMap[String, Array[String]]()
	val pttData = new HashMap[String, HashMap[String, PttItem]]()

	val httpClient = new dispatch.Http {
		import com.ning.http.client._
		val builder = new AsyncHttpClientConfig.Builder()
		builder.setMaximumConnectionsPerHost(200)
			.setMaximumConnectionsTotal(200)
            .build()
		override val client = new AsyncHttpClient(builder.build())
	}

	def parse() {
		if(!pttData.contains(KEY_BOY_GIRL)) {
			pttData += KEY_BOY_GIRL -> new HashMap[String, PttItem]()
			pttDataIndex += KEY_BOY_GIRL -> Array()
		}

		val svc = url("http://rss.ptt.cc/Boy-Girl.xml")
		httpClient(svc OK as.xml.Elem).either.map { e =>
			e match {
				case Right(xml) =>
					println("xml:" + xml)

					(xml \\ "entry").foreach { a =>
						val id = (a \ "id").text
						val nnIndex = (Array(id) ++ pttDataIndex(KEY_BOY_GIRL)).distinct
						if(nnIndex.size > 100) {
							val removeSize = nnIndex.size - 100
							nnIndex.takeRight(removeSize).foreach { in =>
								pttData(KEY_BOY_GIRL).remove(in)
							}
							pttDataIndex += KEY_BOY_GIRL -> nnIndex.take(100)
						} else {
							pttDataIndex += KEY_BOY_GIRL -> nnIndex
						}

						pttData(KEY_BOY_GIRL) += id -> PttItem((a \ "id").text, (a \ "title").text, (a \\ "content" \ "pre").text, 
							(a \\ "author" \ "name").text, (a \ "updated").text, (a \ "link" \ "@href").text, "")
					}

					println("pttDataIndex:" + pttDataIndex(KEY_BOY_GIRL).size + ",pttData(KEY_BOY_GIRL):" 
						+ pttData(KEY_BOY_GIRL).size)
				case Left(err) => 
					println("err:" + err)
			}

		}



	}
}