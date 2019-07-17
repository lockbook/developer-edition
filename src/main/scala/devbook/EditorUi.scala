package devbook

import java.io.File

import javafx.scene.control.{Button, TextArea, TextField}
import javafx.scene.layout.{BorderPane, HBox}
import org.eclipse.jgit.api.Git

class EditorUi(editorHelper: EditorHelper) {

  def getView(git: Git, f: File): BorderPane = {
    val root     = new BorderPane
    val textArea = new TextArea
    val text     = editorHelper.getTextFromFile(f)

    textArea.setText(text.left.get) // TODO handle error
    root.setBottom(getBottom(git, f, textArea))
    root.setCenter(textArea)
    root
  }

  def getBottom(git: Git, file: File, textArea: TextArea): HBox = {
    val save          = new Button("Push")
    val commitMessage = new TextField
    commitMessage.setPromptText("Commit Message")

    save.setOnAction(_ => {
      editorHelper.saveCommitAndPush(commitMessage.getText, textArea.getText, file, git)
    })

    new HBox(commitMessage, save)
  }
}
