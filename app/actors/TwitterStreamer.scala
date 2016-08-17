package actors

import akka.actor.{Actor, ActorRef, Props}
import play.api.{Logger, Play}
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator, Iteratee}
import play.api.libs.json.{JsObject}
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.WS
import play.extras.iteratees.{Encoding, JsonIteratees}
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by Kurtulus Arkan on 8/17/16.
  */
class TwitterStreamer(out: ActorRef) extends Actor {

  override def receive: Receive = {
    case message =>
      val s = message.toString
      Logger.info("Received subscription from a client:" + s)
      TwitterStreamer.subscribe(out, s)
  }
}

object TwitterStreamer {

  def props(out: ActorRef) = Props(new TwitterStreamer(out))

  private var broadcastEnumerator: Option[Enumerator[JsObject]] = None

  def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- Play.configuration.getString("twitter.apiKey")
    apiSecret <- Play.configuration.getString("twitter.apiSecret")
    token <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

  def connect(key: String): Unit = {
    credentials.map { case (consumerKey, requestToken) =>

      val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

      val jsonStream: Enumerator[JsObject] = enumerator &>
        Encoding.decode() &>
        Enumeratee.grouped(JsonIteratees.jsSimpleObject)

      val (be, _) = Concurrent.broadcast(jsonStream)
      broadcastEnumerator = Some(be)

      val url = "https://stream.twitter.com/1.1/statuses/filter.json"

      WS
        .url(url)
        .sign(OAuthCalculator(consumerKey, requestToken))
        .withQueryString("track" -> key)
        .get { response =>
          Logger.info("Status: " + response.status)
          iteratee
        }.map { _ =>
          Logger.info("Twitter stream closed")
        }
    } getOrElse {
      Logger.error("Twitter credentials missing")
    }
  }

  def subscribe(out: ActorRef, key: String): Unit = {

    if (broadcastEnumerator.isEmpty) {
      connect(key)
    }

    val twitterClient = Iteratee.foreach[JsObject] {
      t => out ! t
    }

    broadcastEnumerator.foreach { enumerator =>
      enumerator run twitterClient
    }
  }
}
