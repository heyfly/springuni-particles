package com.springuni.auth.domain.service

import com.springuni.auth.crypto.PasswordSecurity
import com.springuni.auth.domain.model.user.ConfirmationToken
import com.springuni.auth.domain.model.user.Password
import com.springuni.auth.domain.model.user.User
import com.springuni.auth.domain.model.user.UserRepository
import com.springuni.auth.domain.model.user.exceptions.*
import com.springuni.auth.domain.model.userevent.UserEvent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import static com.springuni.auth.domain.model.user.ConfirmationTokenType.EMAIL
import static com.springuni.auth.domain.model.user.ConfirmationTokenType.PASSWORD_RESET
import static com.springuni.auth.domain.model.userevent.UserEventType.*
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.*
import static org.mockito.ArgumentMatchers.*
import static org.mockito.Mockito.*

/**
 * Created by lcsontos on 4/24/17.
 */
@RunWith(MockitoJUnitRunner)
class UserServiceTest extends BaseServiceTest {

  static final NON_EXISTENT_USER_ID = 3L
  static final NON_EXISTENT_USER_EMAIL = "non-existent@springuni.com"
  static final NON_EXISTENT_USER_SCREEN_NAME = "non-existent"

  @Mock PasswordSecurity passwordSecurity
  @Mock UserRepository userRepository

  ConfirmationToken validEmailConfirmationToken
  ConfirmationToken validPasswordResetConfirmationToken
  ConfirmationToken invalidEmailConfirmationToken
  ConfirmationToken invalidPasswordResetConfirmationToken

  User user1
  User user2
  UserService userService

  @Before
  void before() {
    user1 = new User(1L, "test1", "test1@springuni.com")
    validEmailConfirmationToken = user1.addConfirmationToken(EMAIL)
    validPasswordResetConfirmationToken = user1.addConfirmationToken(PASSWORD_RESET)

    user2 = new User(2L, "test2", "test2@springuni.com")
    invalidEmailConfirmationToken = user2.addConfirmationToken(EMAIL).use()
    invalidPasswordResetConfirmationToken = user2.addConfirmationToken(PASSWORD_RESET).use()

    userService = new UserServiceImpl(passwordSecurity, userEventEmitter, userRepository)

    when(userRepository.findById(user1.id)).thenReturn(Optional.of(user1))
    when(userRepository.findByScreenName(user1.screenName)).thenReturn(Optional.of(user1))
    when(userRepository.findByEmail(user1.email)).thenReturn(Optional.of(user1))
    when(userRepository.save(user1)).thenReturn(user1)

    when(userRepository.findById(user2.id)).thenReturn(Optional.of(user2))
    when(userRepository.findByScreenName(user2.screenName)).thenReturn(Optional.of(user2))
    when(userRepository.findByEmail(user2.email)).thenReturn(Optional.of(user2))
    when(userRepository.save(user2)).thenReturn(user2)

    when(userRepository.delete(NON_EXISTENT_USER_ID)).thenThrow(NoSuchUserException)
    when(userRepository.findById(NON_EXISTENT_USER_ID)).thenReturn(Optional.empty())
    when(userRepository.findByScreenName(NON_EXISTENT_USER_SCREEN_NAME)).thenReturn(Optional.empty())
    when(userRepository.findByEmail(NON_EXISTENT_USER_EMAIL)).thenReturn(Optional.empty())
  }

  @Test(expected = EmailIsAlreadyTakenException)
  void testChangeEmail_withExisting() {
    userService.changeEmail(user1.id, user2.email)
  }

  @Test
  void testChangeEmail_withNew() {
    User changedUser = userService.changeEmail(user1.id, NON_EXISTENT_USER_EMAIL)
    assertNotNull(changedUser)
    assertEquals(NON_EXISTENT_USER_EMAIL, changedUser.email)

    assertEmittedUserEvent(EMAIL_CHANGED)
  }

  @Test
  void testChangePassword() {
    ArgumentCaptor<String> rawPasswordCaptor = ArgumentCaptor.forClass(String)
    when(passwordSecurity.ecrypt(rawPasswordCaptor.capture())).thenReturn(new Password("test", "test"))

    User changedUser = userService.changePassword(user1.id, "somepassword")
    assertNotNull(changedUser)
    assertNotNull(changedUser.password)

    verify(passwordSecurity).ecrypt("somepassword")

    assertEmittedUserEvent(PASSWORD_CHANGED)
  }

  @Test(expected = ScreenNameIsAlreadyTakenException)
  void testChangeScreenName_withExisting() {
    userService.changeScreenName(user1.id, user2.screenName)
  }

  @Test
  void testChangeScreenName_withNew() {
    User changedUser = userService.changeScreenName(user1.id, NON_EXISTENT_USER_SCREEN_NAME)
    assertNotNull(changedUser)
    assertEquals(NON_EXISTENT_USER_SCREEN_NAME, changedUser.screenName)

    assertEmittedUserEvent(SCREEN_NAME_CHANGED)
  }

  @Test(expected = InvalidConfirmationTokenException)
  void testConfirmEmail_withInvalidToken() {
    userService.confirmEmail(user1.id, "invalid")
  }

  @Test
  void testConfirmEmail_withValidToken() {
    User changedUser = userService.confirmEmail(user1.id, validEmailConfirmationToken.value)
    assertNotNull(changedUser)

    Optional<ConfirmationToken> confirmationToken = changedUser.getConfirmationToken(
        validEmailConfirmationToken.value)

    assertTrue(confirmationToken.isPresent())
    assertFalse(confirmationToken.get().isValid())
  }

  @Test(expected = InvalidConfirmationTokenException)
  void testConfirmPasswordReset_withInvalidToken() {
    userService.confirmPasswordReset(user1.id, "invalid")
  }

  @Test
  void testConfirmPasswordReset_withValidToken() {
    User changedUser = userService.confirmPasswordReset(user1.id, validPasswordResetConfirmationToken.value)
    assertNotNull(changedUser)

    Optional<ConfirmationToken> confirmationToken = changedUser.getConfirmationToken(validPasswordResetConfirmationToken.value)
    assertNotNull(confirmationToken.isPresent())
    assertFalse(confirmationToken.get().isValid())
  }

  @Test(expected = NoSuchUserException)
  void testDelete_withNonExistent() {
    userService.delete(NON_EXISTENT_USER_ID)
  }

  @Test
  void testDelete_withExistent() {
    userService.delete(user1.id)
    verify(userRepository).delete(user1.id)

    assertEmittedUserEvent(DELETED)
  }

  @Test
  void testFindUser_withNonExistentUserId() {
    Optional<User> user = userService.findUser(NON_EXISTENT_USER_ID)
    assertFalse(user.isPresent())
  }

  @Test
  void testFindUser_withExistentUserId() {
    Optional<User> user = userService.findUser(user1.id)
    assertEquals(user1, user.get())

    user = userService.findUser(user2.id)
    assertEquals(user2, user.get())
  }

  @Test
  void testFindUser_withNonExistentEmailOrScreenName() {
    Optional<User> user = userService.findUser(NON_EXISTENT_USER_SCREEN_NAME)
    assertFalse(user.isPresent())
  }

  @Test
  void testFindUser_withExistentEmailOrScreenName() {
    Optional<User> user = userService.findUser(user1.email)
    assertEquals(user1, user.get())

    user = userService.findUser(user2.email)
    assertEquals(user2, user.get())
  }

  @Test
  void testIsEmailTaken_withNonExistentEmail() {
    assertFalse(userService.isEmailTaken(NON_EXISTENT_USER_EMAIL))
  }

  @Test
  void testIsEmailTaken_withExistentEmail() {
    assertTrue(userService.isEmailTaken(user1.email))
    assertTrue(userService.isEmailTaken(user2.email))
  }

  @Test
  void testIsEmailTaken_withNonExistentScreenName() {
    assertFalse(userService.isScreenNameTaken(NON_EXISTENT_USER_SCREEN_NAME))
  }

  @Test
  void testIsEmailTaken_withExistentScreenName() {
    assertTrue(userService.isScreenNameTaken(user1.screenName))
    assertTrue(userService.isScreenNameTaken(user2.screenName))
  }

  @Test
  void testLogin() {
    when(passwordSecurity.check(isNull(), anyString())).thenReturn(true)
    user1.confirmed = true
    userService.login(user1.email, "valid")

    assertEmittedUserEvent(SIGNIN_SUCCEEDED)
  }

  void testLogin_withBadPassword() {
    user1.confirmed = true
    when(passwordSecurity.check(any(Password), anyString())).thenReturn(false)

    try {
      userService.login(user1.email, "invalid")
      fail("NoSuchUserException expected")
    } catch (NoSuchUserException nsue) {
      // Passed
    }

    assertEmittedUserEvent(SIGNIN_FAILED)
  }

  @Test(expected = NoSuchUserException)
  void testLogin_withNonExistentUser() {
    userService.login(NON_EXISTENT_USER_EMAIL, "invalid")
  }

  @Test(expected = UnconfirmedUserException)
  void testLogin_withUnconfirmedUser() {
    when(passwordSecurity.check(any(Password), anyString())).thenReturn(true)
    userService.login(user1.email, "valid")
  }

  @Test
  void testNextScreenName() {
    when(userRepository.findByScreenName("test")).thenReturn(Optional.of(new User(3L, "test", "test@spriguni.com")))
    String nextScreenName = userService.nextScreenName("test@springuni.com")
    assertEquals("test3", nextScreenName)
    verify(userRepository, times(4)).findByScreenName(anyString())
  }

  @Test
  void testRequestEmailChange() {
    userService.requestEmailChange(user1.id, NON_EXISTENT_USER_EMAIL)

    User user = captureSavedUser()
    assertNotNull(user)
    UserEvent userEvent = captureEmittedUserEvent()
    assertNotNull(userEvent)

    assertThat(user.confirmationTokens, hasItem(userEvent.payload.get()))
    assertEquals(EMAIL_CHANGE_REQUESTED, userEvent.userEventType)
  }

  @Test
  void testRequestPasswordReset() {
    userService.requestPasswordReset(user1.id)

    User user = captureSavedUser()
    assertNotNull(user)
    UserEvent userEvent = captureEmittedUserEvent()
    assertNotNull(userEvent)

    assertThat(user.confirmationTokens, hasItem(userEvent.payload.get()))
    assertEquals(PASSWORD_RESET_REQUESTED, userEvent.userEventType)
  }

  @Test(expected = EmailIsAlreadyTakenException)
  void testSignup_withExistentEmail() {
    User user3 = new User(3L, "test3", "test2@springuni.com")
    userService.signup(user3, "test")
  }

  @Test(expected = ScreenNameIsAlreadyTakenException)
  void testSignup_withExistentScreenName() {
    User user3 = new User(3L, "test2", "test3@springuni.com")
    userService.signup(user3, "test")
  }

  @Test(expected = InvalidEmailException)
  void testSignup_withInvalidEmail() {
    User user3 = new User(3L, "test2", "invalid")
    userService.signup(user3, "test")
  }

  @Test
  void testSignup() {
    User user3 = new User(3L, NON_EXISTENT_USER_SCREEN_NAME, NON_EXISTENT_USER_EMAIL)
    when(userRepository.save(user3)).thenReturn(user3)
    userService.signup(user3, "test")
    verify(passwordSecurity).ecrypt("test")
    assertEmittedUserEvent(SIGNUP_REQUESTED)
  }

  @Test
  void testStore() {

  }

  private User captureSavedUser() {
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User)
    verify(userRepository).save(userCaptor.capture())
    return userCaptor.value
  }

}
