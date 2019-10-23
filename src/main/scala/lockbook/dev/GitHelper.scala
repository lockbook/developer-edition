package lockbook.dev

import java.io.File
import java.net.URI

import com.jcraft.jsch.{JSch, Session}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult.MergeStatus
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.transport.{JschConfigSessionFactory, OpenSshConfig, UsernamePasswordCredentialsProvider}
import org.eclipse.jgit.util.FS

trait GitHelper {
  def cloneRepository(uri: String): Either[GitError, Git]
  def getRepositories: List[Git]
  def getRepoName(git: Git): String
  def commit(message: String, git: Git): Either[GitError, Unit]
  def push(git: Git): Either[GitError, Unit]
  def commitAndPush(message: String, git: Git): Either[GitError, Unit]
  def pull(git: Git): Either[GitError, MergeStatus]
  def sync(git: Git): Either[GitError, Unit]
  def deleteRepo(git: Git): Either[FileError, Unit]
  def pullNeeded(git: Git): Either[UserCanceled, Boolean]
  def localDirty(git: Git): Boolean
}

class GitHelperImpl(gitCredentialHelper: GitCredentialHelper, fileHelper: FileHelper) extends GitHelper {

  val repoFolder = s"${App.path}/repos"

  override def cloneRepository(uri: String): Either[GitError, Git] = {
    getCredentials(new URI(uri)).flatMap(credentials => {
      try {
        Right(
          Git
            .cloneRepository()
            .setURI(uri)
            .setDirectory(new File(s"$repoFolder/${uriToFolder(uri)}"))
            .setCredentialsProvider(credentials)
            .call()
        )
      } catch {
        case _: TransportException =>
          gitCredentialHelper.deleteStoredCredentials(new URI(uri).getHost)
          Left(InvalidCredentials())
        case _: Exception =>
          // TODO
          Left(InvalidCredentials())
      }
    })
  }

  override def commit(message: String, git: Git): Either[GitError, Unit] = {
    try {
      git.add().addFilepattern(".").call()
      git.commit().setMessage(message).call()
      Right(())
    } catch {
      case e: Exception =>
        println(e)
        Left(UserCanceled()) // TODO
    }
  }

  override def push(git: Git): Either[GitError, Unit] = {
    getCredentials(git)
      .flatMap(credentials => {
        try {
          git
            .push()
            .setCredentialsProvider(credentials)
            .call()

          Right(())
        } catch {
          case e: Exception =>
            println(e)
            Left(UserCanceled()) // TODO build this out
        }
      })
  }

  override def commitAndPush(message: String, git: Git): Either[GitError, Unit] = {
    commit(message, git)
      .flatMap(_ => push(git))
  }

  override def sync(git: Git): Either[GitError, Unit] = {
    commit("", git)
      .flatMap(_ => pull(git))
      .flatMap(_ => push(git))
  }

  override def getRepositories: List[Git] = {
    val repos = new File(repoFolder)
    if (repos.exists()) {
      repos
        .listFiles(_.isDirectory)
        .toList
        .map(file => Git.open(file))
    } else {
      List()
    }
  }

  override def deleteRepo(git: Git): Either[FileError, Unit] = {
    val file = git.getRepository.getWorkTree
    fileHelper.recursiveFileDelete(file)
  }

  private def getCredentials(git: Git): Either[UserCanceled, UsernamePasswordCredentialsProvider] =
    getCredentials(getRepoURI(git))

  private def getCredentials(
      uri: URI
  ): Either[UserCanceled, UsernamePasswordCredentialsProvider] = {
    gitCredentialHelper
      .getCredentials(uri.getHost)
      .map(c => new UsernamePasswordCredentialsProvider(c.username, c.password))
  }

  def uriToFolder(uri: String): String = {
    val folderName = uri.split("/").last
    if (folderName.endsWith(".git")) {
      folderName.substring(0, folderName.length - 4)
    } else {
      folderName
    }
  }

  def getRepoName(git: Git): String = {
    val repoUrl = git.getRepository.getConfig
      .getString("remote", "origin", "url")

    uriToFolder(repoUrl)
  }

  def getRepoURI(git: Git): URI = {
    val repoUrl = git.getRepository.getConfig
      .getString("remote", "origin", "url")

    new URI(repoUrl)
  }

  override def pull(git: Git): Either[GitError, MergeStatus] = {
    getCredentials(git).flatMap(credentials => {
      try {
        Right(
          git
            .pull()
            .setCredentialsProvider(credentials)
            .call()
            .getMergeResult
            .getMergeStatus
        )

      } catch {
        case e: Exception =>
          Left(UserCanceled()) // TODO build this out
      }
    })
  }

  override def pullNeeded(git: Git): Either[UserCanceled, Boolean] = {
    getCredentials(git)
      .flatMap { credentials =>
        val doRefsMatch = git
          .fetch()
          .setCredentialsProvider(credentials)
          .call()
          .getAdvertisedRef("HEAD")
          .getObjectId
          .getName
          .equals(git.getRepository.getAllRefs.get("HEAD").getObjectId.getName)

        Right(!doRefsMatch)
      }
  }

  override def localDirty(git: Git): Boolean = !git.status().call().isClean
}

class CustomConfigSessionFactory extends JschConfigSessionFactory {
  override protected def getJSch(hc: OpenSshConfig.Host, fs: FS): JSch = {
    val jsch = super.getJSch(hc, fs)
    jsch.removeAllIdentity()
    jsch.addIdentity("~/.ssh/id_rsa")
    jsch
  }

  override def configure(hc: OpenSshConfig.Host, session: Session): Unit = {}
}
