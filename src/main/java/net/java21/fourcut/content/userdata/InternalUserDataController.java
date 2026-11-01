package net.java21.fourcut.content.userdata;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** auth-service → content-service 내부 API. 탈퇴 정리. */
@RestController
public class InternalUserDataController {

  private final UserDataService userData;

  public InternalUserDataController(UserDataService userData) {
    this.userData = userData;
  }

  @DeleteMapping("/internal/users/{userId}/data")
  public ResponseEntity<Void> delete(@PathVariable String userId) {
    userData.deleteAll(userId);
    return ResponseEntity.noContent().build();
  }
}
