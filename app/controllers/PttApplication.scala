package controller

import play.api._
import play.api.mvc._
import play.Logger

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.collection.JavaConversions._

import akka.actor.{ActorSystem, Props}

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._

import java.util.Calendar
// case class SchedulerBO @JsonCreator() ( 
// 	@scala.reflect.BeanProperty @JsonProperty("m_type")  m_type : String, 
// 	@scala.reflect.BeanProperty @JsonProperty("s_type")  s_type : String, 
// 	@scala.reflect.BeanProperty @JsonProperty("go_server_time")  go_server_time : String, 
// 	@scala.reflect.BeanProperty @JsonProperty("random")  random : Int, 
// 	@scala.reflect.BeanProperty @JsonProperty("display_start_time")  display_start_time : String, 
// 	@scala.reflect.BeanProperty @JsonProperty("display_end_time")  display_end_time : String, 
// 	@scala.reflect.BeanProperty @JsonProperty("day_of_week") day_of_week : Array[String]) {

// 	def matchs(ts : Long) = {
// 		val now = Calendar.getInstance()
// 		now.setTimeInMillis(ts)

// 		val goServerTime = Calendar.getInstance()
// 		goServerTime.set(Calendar.HOUR_OF_DAY, go_server_time.split(":")(0).toInt)
// 		goServerTime.set(Calendar.MINUTE, go_server_time.split(":")(1).toInt)

// 		val displayTime = Calendar.getInstance()
// 		displayTime.set(Calendar.HOUR_OF_DAY, display_start_time.split(":")(0).toInt)
// 		displayTime.set(Calendar.MINUTE, display_start_time.split(":")(1).toInt)

// 		now.after(goServerTime) && 
// 		now.before(displayTime) &&
// 		day_of_week.contains(getDay(now.get(Calendar.DAY_OF_WEEK)))
// 	}

// 	def getDay(dayOfWeek : Int) = {
// 		if(dayOfWeek  == 1) "Sun"
// 		else if(dayOfWeek == 2) "Mon"
// 		else if(dayOfWeek == 3) "Tue"
// 		else if(dayOfWeek == 4) "Wed"
// 		else if(dayOfWeek == 5) "Thu"	
// 		else if(dayOfWeek == 6) "Fri"
// 		else if(dayOfWeek == 7) "Sat"
// 		else ""						
// 	}
// }

object PttApplication extends Controller {
	// implicit val system = Akka.system
	val mapper = new ObjectMapper()

	val test = """{
    "default": [
        {
            "m_type": "mealtime",
            "s_type": "lunch",
            "go_server_time": "11:00",
            "random": 60,
            "display_start_time": "11:30",
            "display_end_time": "13:00",
            "day_of_week":["Mon","Tue","Wed","Thu","Fri","SAT","SUN"]
        }]}"""

	def getBoyGirl() = Action {
		Ok("")
	}
}
