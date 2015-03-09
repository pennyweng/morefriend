// import akka.actor._
// import akka.pattern.ask
// import akka.util.Timeout
 
// import scala.concurrent.duration._
// import scala.concurrent.Future

 
// import play.api._
// import play.api.mvc._
// import play.api.libs.json._
// import play.api.libs.iteratee._
// import play.api.libs.concurrent._
// import play.api.libs.concurrent.Execution.Implicits._
// import play.api.Play.current

// // case class Event(fromUserId: String, text: String, toUserId: String)
// // case class Connected(enumerator: Enumerator[String])

// // case class Join(userId: String, nickName: String, gender: String, picId : String)

// // case class Quit(userId: String)
// // case class WaitEvent( userId : String, num : Int)



// object Actors {
//   lazy val notifier = Akka.system.actorOf(Props[Events])
// }

// object ChatDataSource {
//   import scala.collection.mutable.Map

//   // save every user connection.  key is user id and value is output channel
//   val connected = Map.empty[String, Concurrent.Channel[String]]

//   // currently user talk to which one. key and value are user id
//   var talking = Map.empty[String, String]


//   // waiting table
//   val waitingTable = Map.empty[String, WaitEvent]
// }


// class Events extends Actor {
//   import ChatDataSource._
 
//   def receive = {
//     case Join(username) => {
//       val e = Concurrent.unicast[String]{c =>
//         play.Logger.info("Start")
//         connected = connected + (username -> c)
//       }
//       sender ! Connected(e)
//     }
 
//     case Quit(username) => {
//       connected = connected - username
//     }
 
//     case Event(user, kind, text) => {
//       for(channel <- connected.get(user)){

//         channel.push("")
//       }
//     }
//   }
// }
 
