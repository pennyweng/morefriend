package com.jookershop.morefriend.bo

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
 
import scala.concurrent.duration._
import scala.concurrent.Future

 
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current


object MsgType {
	val JOIN = 1
	val WAIT = 2
	val PASS_REQ = 3
	val PASS_RES = 4
	val TALK_BEGIN = 5
	val TALKING = 6
	val TALK_END = 7
	val SEND_END_MSG = 8
	val RECEIVE_END_MSG = 9
	val NEXT_ONE = 10
	val TALK_BEGIN_CONFIRM = 11
	val TALK_BEGIN_N = 12
	val TALK_END_ALL = 13
}

object ChatDataSource {
  import scala.collection.mutable.Map
  import scala.collection.mutable.MutableList

  val mapper = new ObjectMapper()
  mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)

  // save every user connection.  key is user id and value is output channel
  val connected = Map.empty[String, Concurrent.Channel[String]]

  // currently user talk to which one. key and value are user id
  var talking = Map.empty[String, String]

  // currently user talk to which one. key and value are user id
  var talkingUserId = Map.empty[String, Array[String]]

  // currently user talk to which one. key and value are user id
  var talkingGuid = Map.empty[String, String]

  val yourInfoMap = Map.empty[String, UserInfo]

  // guid, number
  val waitFemale = Map.empty[String, Int]

  // guid, number
  val waitMale = Map.empty[String, Int] 

  // leave message
  val leavedMsg = Map.empty[String, LeaveMsg]

  val blacklist = Map.empty[String, String]

  // guid map to user id
  val allUsersToGuids = Map.empty[String, Array[String]]

  // guid map to user id
  val allGuidToUsers = Map.empty[String, String] 

  // user id map to other users
  val beginTalk = Map.empty[String, Array[String]] 

  // current talking Female
  var lastFemaleUid = ""

  // last wait man
  var lastMaleUid = ""
}

case class Notification @JsonCreator() (
	@scala.beans.BeanProperty @JsonProperty("womenCome") womenCome : Boolean, 
	@scala.beans.BeanProperty @JsonProperty("menCome") menCome : Boolean, 
	@scala.beans.BeanProperty @JsonProperty("womenNum") womenNum : Int, 
	@scala.beans.BeanProperty @JsonProperty("menNum") menNum : Int)



case class BeginTalk @JsonCreator() (
	@scala.beans.BeanProperty @JsonProperty("yourGuid") yourGuid : String, 
	@scala.beans.BeanProperty @JsonProperty("toGuid") toGuid : String, 
	@scala.beans.BeanProperty @JsonProperty("userInfo") userInfo : UserInfo)

case class UserInfo @JsonCreator() (@scala.beans.BeanProperty @JsonProperty("userId") userId : String,
		@scala.beans.BeanProperty @JsonProperty("nickname") nickname : String,
		@scala.beans.BeanProperty @JsonProperty("gender") gender : String,
		@scala.beans.BeanProperty @JsonProperty("findGender") findGender : String,
		@scala.beans.BeanProperty @JsonProperty("picId") picId : String,
		@scala.beans.BeanProperty @JsonProperty("location") location : String,
		@scala.beans.BeanProperty @JsonProperty("gps") gps : String,
		@scala.beans.BeanProperty @JsonProperty("guys") guys : Int
	)

case class WaitNumber @JsonCreator() (@scala.beans.BeanProperty @JsonProperty("yourNum") yourNum : Int, 
	@scala.beans.BeanProperty @JsonProperty("gid") gid : String,
	@scala.beans.BeanProperty @JsonProperty("fname") fname : String,
	@scala.beans.BeanProperty @JsonProperty("fpic") fpic : String
	// @scala.beans.BeanProperty @JsonProperty("canjump") canjump : Boolean
	)
case class UserId @JsonCreator() (@scala.beans.BeanProperty @JsonProperty("yourId") yourId : String)
case class PassInfo @JsonCreator() (@scala.beans.BeanProperty @JsonProperty("yourNum") yourNum : Int, 
	@scala.beans.BeanProperty @JsonProperty("passNum") passNum : Int,
	@scala.beans.BeanProperty @JsonProperty("passCount") passCount : Int,
	@scala.beans.BeanProperty @JsonProperty("gid") gid : String
	)

// case class GuyInfo @JsonCreator() (@scala.beans.BeanProperty @JsonProperty("toId") toId : String,
// 		@scala.beans.BeanProperty @JsonProperty("nickname") nickname : String,
// 		@scala.beans.BeanProperty @JsonProperty("gender") gender : String,
// 		@scala.beans.BeanProperty @JsonProperty("picId") picId : String,
// 		@scala.beans.BeanProperty @JsonProperty("location") location : String,
// 		@scala.beans.BeanProperty @JsonProperty("gps") gps : String
// 	)


case class TalkMsg @JsonCreator() (@scala.beans.BeanProperty @JsonProperty("fromId") fromId : String, 
	@scala.beans.BeanProperty @JsonProperty("toId") toId : String,
	@scala.beans.BeanProperty @JsonProperty("msg") msg : String,
	@scala.beans.BeanProperty @JsonProperty("ts") ts : Long,
	@scala.beans.BeanProperty @JsonProperty("fromGid") fromGid : String,
	@scala.beans.BeanProperty @JsonProperty("toGid") toGid : String
	)

case class EndMsg @JsonCreator() (
	@scala.beans.BeanProperty @JsonProperty("fromId") fromId : String,
	@scala.beans.BeanProperty @JsonProperty("toId") toId : String,
	@scala.beans.BeanProperty @JsonProperty("msg") msg : String,
	@scala.beans.BeanProperty @JsonProperty("nickname") nickname : String, // your nickname
	@scala.beans.BeanProperty @JsonProperty("seeAgain") seeAgain : Int, // 0:see 1: not see
	@scala.beans.BeanProperty @JsonProperty("fromGid") fromGid : String,
	@scala.beans.BeanProperty @JsonProperty("toGid") toGid : String

	)

case class LeaveMsg( nickname: String, msg : String)
// case class EndMsg @JsonCreator() (@scala.beans.BeanProperty @JsonProperty("toId") toId : String,
// 	@scala.beans.BeanProperty @JsonProperty("msg") msg : String,
// 	@scala.beans.BeanProperty @JsonProperty("nickname") nickname : String
// 	)
