
package object simplez {

  import scala.language.higherKinds

  type Id[A] = A

  type Reader[A, B] = Kleisli[Id, A, B]

  type ~>[F[_], G[_]] = NaturalTransformation[F, G]

  object id {
    implicit val idMonad = new Monad[Id] {
      def pure[A](a: A): Id[A] = a

      def flatMap[A, B](F: Id[A])(f: A => Id[B]): Id[B] = f(F)
    }

    implicit val identityTransformation = new NaturalTransformation[Id, Id] {
      def apply[A](a: Id[A]) = a
    }
  }

  object Reader {
    def apply[A, B](f: A => B): Reader[A, B] = Kleisli.kleisli[Id, A, B](f)

    implicit def monadInstance[X]: Monad[({ type l[a] = Reader[X, a] })#l] = new Monad[({ type l[a] = Reader[X, a] })#l] {
      override def flatMap[A, B](F: Reader[X, A])(f: (A) => Reader[X, B]): Reader[X, B] = flatMap(F)(f)

      override def pure[A](a: A): Reader[X, A] = Reader { x: X => a }
    }
  }

}
