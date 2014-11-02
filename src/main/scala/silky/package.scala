import scala.annotation.implicitNotFound
import scala.reflect.runtime.universe._
import scala.reflect.runtime._
import scala.util.Properties.lineSeparator

package object silky {

  @implicitNotFound("No member of type class ShowTree in scope for ${T}")
  trait ShowTree[T] {
    def treeStringOf(value: T): String
  }

  implicit class TreeString[A](a: A) {

    /** @return A readable string representation of this value */
    def asTreeString(implicit converter: Option[ShowTree[A]] = None): String = a match {
      case (k, v)            ⇒ s"$k = ${v.asTreeString}"
      case v: Stream[_]      ⇒ treeStringOf(v)
      case v: Traversable[_] ⇒ treeStringOf(v)
      case Some(v)           ⇒ s"Some(${v.asTreeString})"
      case Left(v)           ⇒ s"Left(${v.asTreeString})"
      case Right(v)          ⇒ s"Right(${v.asTreeString})"
      case v: Product        ⇒ treeStringOf(v)
      case v: String         ⇒ s""""$v""""
      case null              ⇒ "null"
      case _                 ⇒ converter.fold(a.toString) { _.treeStringOf(a) }
    }

    private def treeStringOf(s: Stream[_]): String = {
      val result = s.map(_.asTreeString)
      if (result.size < 2) result.mkString(", ") else result.map(indent).mkString(lineSeparator)
    }

    private def treeStringOf(t: Traversable[_]): String =
      if (t.size < 2)
        s"${t.stringPrefix}(${t.map(_.asTreeString).mkString(", ")})"
      else
        s"""${t.stringPrefix}($lineSeparator${
          t.map(_.asTreeString)
            .map(indent)
            .mkString(lineSeparator)
        }$lineSeparator)"""

    private def treeStringOf(p: Product): String = {
      def isAccessible(ts: TermSymbol) = (ts.isVal || ts.isVar) && ts.getter != NoSymbol && !ts.getter.isPrivate

      val fields = currentMirror.reflect(p).symbol.typeSignature.members.toStream
        .collect { case a: TermSymbol ⇒ a }
        .filterNot(_.isMethod)
        .filterNot(_.isModule)
        .filterNot(_.isClass)
        .filter(s ⇒ s.isTerm && isAccessible(s.asTerm))
        .map(currentMirror.reflect(p).reflectField)
        .map(f ⇒ f.symbol.name.toString.trim → f.get)
        .reverse

      p.productArity match {
        case 0 ⇒ p.productPrefix
        case n if n < 2 ⇒ s"${p.productPrefix}(${fields.asTreeString})"
        case _ ⇒ s"${p.productPrefix}($lineSeparator${fields.asTreeString}$lineSeparator)"
      }
    }

    private def indent: String ⇒ String = string ⇒ string.lines.toStream match {
      case h +: t ⇒ (s"- $h" +: t.map{ "| " + _ }) mkString lineSeparator
      case _      ⇒ "- "
    }
  }
}