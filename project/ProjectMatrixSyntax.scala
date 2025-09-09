import sbt.internal.ProjectMatrix

/** Provides missing syntax for [[ProjectMatrix]] */
object ProjectMatrixSyntax {
  implicit class RichProjectMatrix(val inner: ProjectMatrix) extends AnyVal {

    /** Same as [[sbt.Project.configure]] but for [[ProjectMatrix]] */
    def configureMatrix(f: ProjectMatrix => ProjectMatrix): ProjectMatrix = {
      f(inner)
    }
  }
}
