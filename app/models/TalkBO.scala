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
	@scala.reflect.BeanProperty @JsonProperty("womenCome") womenCome : Boolean, 
	@scala.reflect.BeanProperty @JsonProperty("menCome") menCome : Boolean, 
	@scala.reflect.BeanProperty @JsonProperty("womenNum") womenNum : Int, 
	@scala.reflect.BeanProperty @JsonProperty("menNum") menNum : Int)



case class BeginTalk @JsonCreator() (
	@scala.reflect.BeanProperty @JsonProperty("yourGuid") yourGuid : String, 
	@scala.reflect.BeanProperty @JsonProperty("toGuid") toGuid : String, 
	@scala.reflect.BeanProperty @JsonProperty("userInfo") userInfo : UserInfo)

case class UserInfo @JsonCreator() (@scala.reflect.BeanProperty @JsonProperty("userId") userId : String,
		@scala.reflect.BeanProperty @JsonProperty("nickname") nickname : String,
		@scala.reflect.BeanProperty @JsonProperty("gender") gender : String,
		@scala.reflect.BeanProperty @JsonProperty("findGender") findGender : String,
		@scala.reflect.BeanProperty @JsonProperty("picId") picId : String,
		@scala.reflect.BeanProperty @JsonProperty("location") location : String,
		@scala.reflect.BeanProperty @JsonProperty("gps") gps : String,
		@scala.reflect.BeanProperty @JsonProperty("guys") guys : Int
	)

case class WaitNumber @JsonCreator() (@scala.reflect.BeanProperty @JsonProperty("yourNum") yourNum : Int, 
	@scala.reflect.BeanProperty @JsonProperty("gid") gid : String,
	@scala.reflect.BeanProperty @JsonProperty("fname") fname : String,
	@scala.reflect.BeanProperty @JsonProperty("fpic") fpic : String
	// @scala.reflect.BeanProperty @JsonProperty("canjump") canjump : Boolean
	)
case class UserId @JsonCreator() (@scala.reflect.BeanProperty @JsonProperty("yourId") yourId : String)
case class PassInfo @JsonCreator() (@scala.reflect.BeanProperty @JsonProperty("yourNum") yourNum : Int, 
	@scala.reflect.BeanProperty @JsonProperty("passNum") passNum : Int,
	@scala.reflect.BeanProperty @JsonProperty("passCount") passCount : Int,
	@scala.reflect.BeanProperty @JsonProperty("gid") gid : String
	)

// case class GuyInfo @JsonCreator() (@scala.reflect.BeanProperty @JsonProperty("toId") toId : String,
// 		@scala.reflect.BeanProperty @JsonProperty("nickname") nickname : String,
// 		@scala.reflect.BeanProperty @JsonProperty("gender") gender : String,
// 		@scala.reflect.BeanProperty @JsonProperty("picId") picId : String,
// 		@scala.reflect.BeanProperty @JsonProperty("location") location : String,
// 		@scala.reflect.BeanProperty @JsonProperty("gps") gps : String
// 	)


case class TalkMsg @JsonCreator() (@scala.reflect.BeanProperty @JsonProperty("fromId") fromId : String, 
	@scala.reflect.BeanProperty @JsonProperty("toId") toId : String,
	@scala.reflect.BeanProperty @JsonProperty("msg") msg : String,
	@scala.reflect.BeanProperty @JsonProperty("ts") ts : Long,
	@scala.reflect.BeanProperty @JsonProperty("fromGid") fromGid : String,
	@scala.reflect.BeanProperty @JsonProperty("toGid") toGid : String
	)

case class EndMsg @JsonCreator() (
	@scala.reflect.BeanProperty @JsonProperty("fromId") fromId : String,
	@scala.reflect.BeanProperty @JsonProperty("toId") toId : String,
	@scala.reflect.BeanProperty @JsonProperty("msg") msg : String,
	@scala.reflect.BeanProperty @JsonProperty("nickname") nickname : String, // your nickname
	@scala.reflect.BeanProperty @JsonProperty("seeAgain") seeAgain : Int, // 0:see 1: not see
	@scala.reflect.BeanProperty @JsonProperty("fromGid") fromGid : String,
	@scala.reflect.BeanProperty @JsonProperty("toGid") toGid : String

	)

case class LeaveMsg( nickname: String, msg : String)
// case class EndMsg @JsonCreator() (@scala.reflect.BeanProperty @JsonProperty("toId") toId : String,
// 	@scala.reflect.BeanProperty @JsonProperty("msg") msg : String,
// 	@scala.reflect.BeanProperty @JsonProperty("nickname") nickname : String
// 	)
