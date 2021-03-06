package simplez

import scala.language.{ higherKinds, implicitConversions }

/**
 * A Semigroup defines an associative binary function.
 *
 * {{{
 *  Semigroup[Int].mappend(3,4)
 * }}}
 *
 * === Laws ===
 * Associativity:  `a append (b append c) = (a append b) append c`
 *
 * @tparam A the type of the semigroup
 * @see [[http://en.wikipedia.org/wiki/Semigroup]]
 */
trait Semigroup[A] {
  /**
   * The associative binary function.
   * @group("base")
   */
  def append(a: A, b: A): A
}

object Semigroup {
  def apply[A](implicit F: Semigroup[A]): Semigroup[A] = F
}

/**
 * A Monoid is a special [[Semigroup]] together with an identity element call mzero.
 *
 * === Law ===
 * Identity : mzero append a = a
 *
 * @tparam A the type of the monoid.
 * @see [[http://en.wikipedia.org/wiki/Monoid]]
 */
trait Monoid[A] extends Semigroup[A] {
  /**
   * the identity element.
   * @group("base")
   */
  def zero: A
}

object Monoid {
  def apply[A](implicit F: Monoid[A]): Monoid[A] = F
}

/**
 * A functor is a structure which defines a mapping from F[A] to F[B].
 * Strictly speaking this a covariant functor.
 *
 * @tparam F a type constructor.
 * @see [[http://en.wikipedia.org/wiki/Functor]]
 */
trait Functor[F[_]] {
  /**
   * the mapping function.
   * {{{
   * 	val listString = Functor[List].map(listInt){ (a:Int) => a.toString }
   * }}}
   *
   * === Laws ===
   * Identity: `F[A].map(x => x) = F[A]`
   * Composition: `F[A].map((b => c) compose (a => v)) = F[A].map(a => b).map(b=>c)`
   */
  def map[A, B](F: F[A])(f: A => B): F[B]
}

object Functor {
  def apply[F[_]](implicit F: Functor[F]) = F
}

trait ContravariantFunctor[F[_]] {
  /**
   *
   * @group("base")
   */
  def contramap[A, B](fa: F[A])(f: B => A): F[B]
}

object ContravariantFunctor {
  def apply[F[_]](implicit F: ContravariantFunctor[F]) = F
}

/**
 *
 * @tparam F a type constructor.
 *
 * @see [[simplez.syntax.ApplicativeBuilder]] for the famous `|@|` (Admiral Akbhar) operator.
 */
trait Applicative[F[_]] extends Functor[F] with GenApApplyFunctions[F] {

  def pure[A](a: A): F[A]

  /**
   * execute a function f with a single parameter within a context F within that context fa : F[A].
   */
  def ap[A, B](F: => F[A])(f: => F[A => B]): F[B]

  /**
   * override map with ap of an Applicative.
   * As we have the means to put anything in our F now via pure the implementation looks like
   *  {{{
   *    ap(fa)(pure(f))
   *  }}}
   */
  override def map[A, B](fa: F[A])(f: A => B): F[B] =
    ap(fa)(pure(f))

}

object Applicative {
  def apply[F[_]](implicit F: Applicative[F]): Applicative[F] = F
}

/**
 * A Monad specialises a [[Functor]].
 *
 * @tparam F[_] a type constructor
 * @see [[http://ncatlab.org/nlab/show/monad+%28in+computer+science%29]]
 */
trait Monad[F[_]] extends Applicative[F] {

  def flatMap[A, B](F: F[A])(f: A => F[B]): F[B]

  /**
   * Implementation of `map` in terms of `flatMap`
   *
   */
  override def map[A, B](F: F[A])(f: A => B): F[B] = {
    flatMap(F)(a => pure(f(a)))
  }

  def ap[A, B](fa: => F[A])(f: => F[A => B]): F[B] = {
    lazy val fa0: F[A] = fa
    // map(fa0) is a partially applied function
    // val  m : (A => B) => F[B] = map(fa0) _
    flatMap(f)(map(fa0))
  }
}

object Monad {
  def apply[F[_]](implicit F: Monad[F]): Monad[F] = F
}

trait Foldable[F[_]] {
  /**
   * Map each element of the structure to a [[Monoid]], and combine the
   * results.
   */
  def foldMap[A, B](fa: F[A])(f: A => B)(implicit F: Monoid[B]): B

  def foldRight[A, B](fa: F[A], z: => B)(f: (A, B) => B): B

  /**
   * @group("derived")
   *
   * Sum with the Monoid over the identity function.
   */
  def fold[A](fa: F[A])(implicit F: Monoid[A]): A = foldMap(fa)(a => a)
}

case object Foldable {
  def apply[F[_]](implicit F: Foldable[F]): Foldable[F] = F
}

trait Traverse[F[_]] extends Functor[F] with Foldable[F] { self =>

  /**
   * @group("base")
   */
  def traverse[G[_]: Applicative, A, B](fa: F[A])(f: A => G[B]): G[F[B]]

  /**
   * Traverse with the identity function.
   * 	@group("derived")
   */
  def sequence[G[_]: Applicative, A](fga: F[G[A]]): G[F[A]] =
    traverse(fga)(ga => ga)

}

object Traverse {
  def apply[F[_]](implicit F: Traverse[F]): Traverse[F] = F
}

/**
 * A natural transformation defines a kind of conversion between type constructors.
 *
 * @tparam F
 * @tparam G
 * @see [[http://en.wikipedia.org/wiki/Natural_transformation]]
 */
trait NaturalTransformation[-F[_], +G[_]] {
  def apply[A](F: F[A]): G[A]
}

object NaturalTransformation {
  def apply[F[_], G[_]](implicit NT: NaturalTransformation[F, G]): NaturalTransformation[F, G] = NT

  /**
   * defines an implicit conversion from a natural transformation to a function `F[A] => G[A]`
   */
  implicit def reify[F[_], G[_], A](NT: F ~> G): F[A] => G[A] = { f => NT(f) }
}

sealed trait Free[F[_], A] {
  def flatMap[B](f: A => Free[F, B]): Free[F, B] =
    this match {
      case Return(a) => f(a)
      case Bind(fx, g) =>
        Bind(fx, g andThen ((free: Free[F, A]) => free flatMap f))
    }

  def map[B](f: A => B): Free[F, B] =
    flatMap(a => Return(f(a)))

  def foldMap[G[_]: Monad](f: F ~> G): G[A] =
    this match {
      case Return(a) => Monad[G].pure(a)
      case Bind(fx, g) =>
        Monad[G].flatMap(f(fx)) { a =>
          g(a).foldMap(f)
        }
    }
}

final case class Return[F[_], A](a: A)
  extends Free[F, A]

final case class Bind[F[_], I, A](a: F[I],
  f: I => Free[F, A]) extends Free[F, A]

/**
 *
 */
trait Kleisli[F[_], A, B] {

  import simplez.Kleisli._

  def run(a: A): F[B]

  def andThen[C](k: Kleisli[F, B, C])(implicit b: Monad[F]): Kleisli[F, A, C] =
    kleisli((a: A) => b.flatMap(this.run(a))(k.run _))

  def >=>[C](k: Kleisli[F, B, C])(implicit b: Monad[F]): Kleisli[F, A, C] = this andThen k

  def >==>[C](f: B => F[C])(implicit b: Monad[F]) = this andThen kleisli(f)

  def compose[C](k: Kleisli[F, C, A])(implicit b: Monad[F]): Kleisli[F, C, B] = {
    k >=> this
  }

  def <=<[C](k: Kleisli[F, C, A])(implicit b: Monad[F]): Kleisli[F, C, B] = this compose k

  def <==<[C](f: C => F[A])(implicit b: Monad[F]): Kleisli[F, C, B] = this compose kleisli(f)

  def map[C](f: B => C)(implicit G: Functor[F]): Kleisli[F, A, C] = kleisli {
    (a: A) =>
      val b = this.run(a)
      G.map(b)(f)
  }

  def mapK[G[_], C](f: F[B] => G[C])(implicit F: Functor[F]): Kleisli[G, A, C] = kleisli {
    a: A =>
      f(this.run(a))
  }

  def flatMap[C](f: B => Kleisli[F, A, C])(implicit G: Monad[F]): Kleisli[F, A, C] = kleisli {
    (r: A) =>
      val b = this.run(r)
      G.flatMap(b) { b: B => f(b).run(r) }
  }

  def flatMapK[C](f: B => F[C])(implicit F: Monad[F]): Kleisli[F, A, C] =
    kleisli(a => F.flatMap(run(a))(f))

  def local[AA](f: AA => A): Kleisli[F, AA, B] = kleisli(f andThen run)
}

object Kleisli {

  trait KleisliMonad[F[_], R] extends Monad[({ type l[a] = Kleisli[F, R, a] })#l] {
    implicit def F: Monad[F]
    override def pure[A](a: A): Kleisli[F, R, A] = kleisli { _ => F.pure(a) }

    override def flatMap[A, B](fa: Kleisli[F, R, A])(f: A => Kleisli[F, R, B]): Kleisli[F, R, B] = fa.flatMap(f)
    override def ap[A, B](fa: => Kleisli[F, R, A])(f: => Kleisli[F, R, A => B]): Kleisli[F, R, B] =
      kleisli[F, R, B](r => F.ap(fa.run(r))(f.run(r)))
  }

  def kleisli[F[_], A, B](f: A => F[B]): Kleisli[F, A, B] = new Kleisli[F, A, B] {
    def run(a: A): F[B] = f(a)
  }

  implicit def kleisliInstance[T[_], R](implicit M: Monad[T]): KleisliMonad[T, R] = new KleisliMonad[T, R] {
    override implicit def F: Monad[T] = M
  }
}

trait State[S, A] {
  def run(s: S): (S, A)

  def map[B](f: A => B): State[S, B] = State[S, B] {
    s =>
      val (s1, a) = run(s)
      (s1, f(a))
  }

  def flatMap[B](f: A => State[S, B]): State[S, B] = State[S, B] {
    s =>
      val (s1, a) = run(s)
      val b = f(a)
      b.run(s1)
  }
}

object State {
  def apply[S, A](f: S => (S, A)) = new State[S, A] {
    def run(s: S) = f(s)
  }
}

/**
 * A Writer keeps track of information on the right hand side of its (W,A) tuple, mapping over the A and appending
 * the W side where necessary.
 *
 * This implementation lacks the details and usefulness of the scalaz implementation as it omits the
 * F[_] typeconstructor for values of W.
 *
 *
 * {{{
 *   def addition(x1: Int, y1: Int) = for {
 * x <- x1.set(List(s"x =  $x1"))
 * y <- y1.set(List(s"y =  $y1"))
 * result <- (x + y).set(List(s"Adding values ${x + y}"))
 * } yield result
 *
 * val Writer((w,a)) = addition(10, 20)
 * }}}
 *
 * @constructor
 * Construct a new Writer with a initial tuple (W,A).
 *
 * @tparam W the "log" side
 * @tparam A the "value side"
 */
final case class Writer[W, A](run: (W, A)) {

  /**
   * Map over the A.
   */
  def map[B](f: A => B): Writer[W, B] = {
    val (w, a) = run
    Writer(w -> f(a))
  }

  /**
   * flatMap concatenating the two written sides.
   */
  def flatMap[B](f: A => Writer[W, B])(implicit s: Semigroup[W]): Writer[W, B] = {
    val (w, a) = run
    val (w1, b) = f(a).run
    Writer(s.append(w, w1) -> b)
  }

  /**
   * Return the written W side.
   */
  def written = run._1

  /**
   * Return the value A side.
   */
  def value = run._2

  /**
   * map over the written side.
   */
  def mapWritten[W2](f: W => W2)(implicit F: Functor[Id]): Writer[W2, A] = {
    val w2 = F.map(written)(f)
    Writer((w2 -> value))
  }

  /**
   * Map over the tuple (W,A) of the Writer.
   * Strange naming - it does not map of the value A! map does that.
   */
  def mapValue[X, B](f: ((W, A)) => (X, B))(implicit F: Functor[Id]): Writer[X, B] =
    Writer(F.map(run)(f))

  /**
   * Prepend a W to a writer.
   * {{{
   * "String" <++: writer
   * }}}
   */
  def <++:(w: => W)(implicit F: Functor[Id], W: Semigroup[W]): Writer[W, A] =
    mapWritten(W.append(w, _))

  /**
   * Append a W to a writer.
   * {{{
   * 		writer :++> "String"
   * }}}
   */
  def :++>(w: => W)(implicit F: Functor[Id], W: Semigroup[W]): Writer[W, A] =
    mapWritten(W.append(_, w))

  /**
   * Clear the written side with a Monoid.
   */
  def reset(implicit W: Monoid[W]): Writer[W, A] = {
    Writer(W.zero -> value)
  }
}

/**
 * A monad transformer which encapsulates the monad Option in any Monad F.
 *
 * E.g. a `Future[Option[A]]` can be encapsulated in a OptionT[Future, A].
 * Anytime you have the structure M[N[A] and you do not care about the outer N
 * at the moment, you can choose an NT Monad Transformer, e.g.
 * ListT[Option,A] if you have an Option[List[A]] or a
 * OptionT[Future, A] if you have a Future[Option[A]]
 *
 */
final case class OptionT[F[_], A](run: F[Option[A]]) {
  self =>

  def map[B](f: A => B)(implicit F: Functor[F]): OptionT[F, B] =
    new OptionT[F, B](mapO((opt: Option[A]) => opt map f))

  def flatMap[B](f: A => OptionT[F, B])(implicit F: Monad[F]): OptionT[F, B] = new OptionT[F, B](
    F.flatMap(self.run) {
      // partial functions: expected A => F[B]
      case None => F.pure(None)
      case Some(z) => f(z).run
    })

  /**
   * This works as an internal helper function to make it easier to rewrite the API of the underlying monad.
   * @param f the function you would like to invoke on the underlying monad
   * @param F for this to work we need an implicit functor instance
   * @tparam B the result type
   * @return the result in the outer monad.
   */
  private def mapO[B](f: Option[A] => B)(implicit F: Functor[F]): F[B] = F.map(run)(f)
}

case object OptionT {
  /**
   * This is essentially a function from the MonadTrans type class.
   * Considering you have a Future[A] but need a Future[Option[A]] aka
   * a OptionT[Future, A] you can lift the future into the monad of
   * the monad transformer.
   */
  def liftM[G[_], A](a: G[A])(implicit G: Monad[G]): OptionT[G, A] =
    OptionT[G, A](G.map(a)(a => Some(a)))
}

final case class ListT[F[A], A](run: F[List[A]]) {
  self =>
  def map[B](f: A => B)(implicit F: Functor[F]): ListT[F, B] =
    ListT[F, B](mapO((list: List[A]) => list map f))

  def flatMap[B](f: A => ListT[F, B])(implicit F: Monad[F]): ListT[F, B] = ListT[F, B](
    F.flatMap(self.run) {
      case Nil => F.pure(Nil)
      case nonEmpty => nonEmpty.map(f).reduce(_ ++ _).run
    }
  )

  def headOption(implicit F: Functor[F]): F[Option[A]] = mapO(_.headOption)

  def head(implicit F: Functor[F]): F[A] = mapO(_.head)

  def isEmpty(implicit F: Functor[F]): F[Boolean] = mapO(_.isEmpty)

  def ++(bs: => ListT[F, A])(implicit F: Monad[F]): ListT[F, A] = new ListT(F.flatMap(run) { list1 =>
    F.map(bs.run) { list2 =>
      list1 ++ list2
    }
  })

  private def mapO[B](f: List[A] => B)(implicit F: Functor[F]) = F.map(run)(f)
}

case object ListT {
  /**
   *
   * @see [[OptionT.liftM]]
   */
  def liftM[G[_], A](a: G[A])(implicit G: Monad[G]): ListT[G, A] = ListT[G, A](
    G.map(a)(a => List(a)))
}

sealed trait CValidation[A, B] {

  def ap[C](x: => CValidation[A, B => C])(implicit S: Semigroup[A]): CValidation[A, C] = {
    (this, x) match {
      case (CRight(a), CRight(f)) => CRight(f(a))
      case (CRight(a), CLeft(error)) => CLeft(error)
      case (CLeft(error), CRight(f)) => CLeft(error)
      case (CLeft(error1), CLeft(error2)) => CLeft(S.append(error1, error2))
    }
  }

  def map[C](f: B => C): CValidation[A, C] = {
    this match {
      case CLeft(error) => CLeft[A, C](error)
      case CRight(good) => CRight[A, C](f(good))
    }
  }

  def flatMap[C](f: (B) => CValidation[A, C]): CValidation[A, C] = {
    this match {
      case CLeft(error) => CLeft[A, C](error)
      case CRight(good) => f(good)
    }
  }

}

object CValidation {

  implicit def cvalidationInstances1[X](implicit SG: Semigroup[X]): Monad[({ type l[a] = CValidation[X, a] })#l] = new Monad[({ type l[a] = CValidation[X, a] })#l] {

    override def flatMap[A, B](F: CValidation[X, A])(f: (A) => CValidation[X, B]): CValidation[X, B] = {
      F.flatMap(f)
    }

    override def pure[A](a: A): CValidation[X, A] = CRight(a)

    /**
     * It is required to overwrite ap as we need to keep collecting the results.
     */
    override def ap[A, B](F: => CValidation[X, A])(f: => CValidation[X, (A) => B]): CValidation[X, B] = F.ap(f)(SG)
  }

}
final case class CLeft[A, B](run: A) extends CValidation[A, B]

final case class CRight[A, B](run: B) extends CValidation[A, B]