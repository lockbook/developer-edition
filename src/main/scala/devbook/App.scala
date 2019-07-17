package devbook

import javafx.application.Application
import javafx.stage.Stage

object App {
  val path = s"${System.getProperty("user.home")}/.devbook"

  def main(args: Array[String]) {
    Application.launch(classOf[App], args: _*)
  }
}

class App extends Application {
  override def start(primaryStage: Stage): Unit = {
    val gitCredentialHelper                = new GitCredentialHelperImpl
    val encryptionHelper: EncryptionHelper = new EncryptionImpl
    val lockfile: Lockfile                 = new LockfileImpl(encryptionHelper)
    val passwordHelper: PasswordHelper     = new PasswordHelperImpl(lockfile, encryptionHelper)
    val gitHelper: GitHelper               = new GitHelperImpl(gitCredentialHelper)
    val editorHelper                       = new EditorHelperImpl(encryptionHelper, passwordHelper, gitHelper)

    val newPasswordUi = new NewPasswordUi(lockfile)
    val unlockUi      = new UnlockUi(passwordHelper)
    val repositoryUi  = new RepositoryUi(gitHelper)
    val fileTreeUi    = new FileTreeUi
    val editorUi      = new EditorUi(editorHelper)

    val uiOrchestrator =
      new UiOrchestrator(lockfile, unlockUi, newPasswordUi, repositoryUi, fileTreeUi, editorUi)
    uiOrchestrator.showView(primaryStage)
  }
}
