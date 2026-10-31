package net.java21.fourcut.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** 만화함, 게시, 피드, 좋아요·댓글, 팔로우. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ContentApplication {

  public static void main(String[] args) {
    SpringApplication.run(ContentApplication.class, args);
  }
}
