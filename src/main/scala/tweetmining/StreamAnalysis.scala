package tweetmining

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.dstream.DStream
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.dstream.DStream

object StreamAnalysis {
  val conf = new SparkConf().setMaster("local[6]").setAppName("Spark batch processing")
  val sc = new SparkContext(conf)
  val streamingContext = new StreamingContext(sc, Seconds(5))
  sc.setLogLevel("WARN")
  def main(args: Array[String]): Unit = {
    println("Twitter data stream processing")
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "localhost:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "twitter-consumer",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean))

    val topics = Array("tweets")
    val tweetStream = KafkaUtils.createDirectStream[String, String](
      streamingContext,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams))

    //add your code here
    // filter stream
//    tweetStream.foreachRDD { rdd =>
//      rdd.map(consRecord => consRecord.value())
//        .filter(tweet => tweet.toLowerCase().contains("friend"))
//        .foreach(println)
//    }

    // output tweet & sentiment stream
//    tweetStream.foreachRDD { rdd =>
//      rdd.map(consRecord => consRecord.value())
//        .map(tweet => (tweet, TweetUtilities.getSentiment(tweet)))
//        .foreach(println)
//    }
    
    // output top 10 & bottom 10 hashtags/mentions by sentiments 
    tweetStream.foreachRDD { rdd =>
      val tagsSentiments = rdd.map(consRecord => consRecord.value())
        .flatMap{ tweet =>
          val mentions = TweetUtilities.getMentions(tweet)
            .map(mention => (mention, (TweetUtilities.getSentiment(tweet), 1d)))
          val hashTags = TweetUtilities.getHashTags(tweet)
            .map(hashTag => (hashTag, (TweetUtilities.getSentiment(tweet), 1d)))
          mentions ++ hashTags
        }
        .reduceByKey((pair1, pair2) => (pair1._1 + pair2._1, pair1._2 + pair2._2)) // val: (sum, coun)
        .map(pair => (pair._1, pair._2._1 / pair._2._2)) // val: avg
        .map(_.swap)
        .cache();
      
      println(">> Bottom 10");
      tagsSentiments.takeOrdered(10)
        .map(_.swap)
        .foreach(println);
      
      println(">> Top 10");
      tagsSentiments.top(10)
        .map(_.swap)
        .foreach(println);


    }

    streamingContext.start()
    streamingContext.awaitTermination()
  }
}