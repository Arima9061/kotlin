public final class Final /* Final*/ {
  public  Final();//  .ctor()
}

public final class In /* In*/<Z>  {
  public  In();//  .ctor()
}

public final class Inv /* Inv*/<E>  {
  public  Inv();//  .ctor()
}

public final class JvmWildcardAnnotationsKt /* JvmWildcardAnnotationsKt*/ {
  @kotlin.jvm.JvmSuppressWildcards(suppress = false)
  @org.jetbrains.annotations.NotNull()
  public static final Out<? extends Open> bar();//  bar()

  @kotlin.jvm.JvmSuppressWildcards(suppress = false)
  public static final int foo(boolean, @org.jetbrains.annotations.NotNull() Out<? extends java.lang.Integer>);//  foo(boolean, Out<? extends java.lang.Integer>)

  @kotlin.jvm.JvmSuppressWildcards(suppress = true)
  @org.jetbrains.annotations.NotNull()
  public static final In<Open> foo3();//  foo3()

  @kotlin.jvm.JvmSuppressWildcards(suppress = true)
  @org.jetbrains.annotations.NotNull()
  public static final Out<T> foo2();//  foo2()

  @kotlin.jvm.JvmSuppressWildcards(suppress = true)
  public static final int bar(boolean, @org.jetbrains.annotations.NotNull() In<java.lang.Long>, @kotlin.jvm.JvmSuppressWildcards(suppress = false) long);//  bar(boolean, In<java.lang.Long>, @kotlin.jvm.JvmSuppressWildcards(suppress = false) long)

  @kotlin.jvm.JvmSuppressWildcards(suppress = true)
  public static final void deepOpen(@org.jetbrains.annotations.NotNull() Out<Out<Out<Open>>>);//  deepOpen(Out<Out<Out<Open>>>)

  @org.jetbrains.annotations.NotNull()
  public static final @kotlin.jvm.JvmSuppressWildcards() OutPair<Open, @kotlin.jvm.JvmWildcard() ? extends OutPair<Open, ? extends Out<Open>>> combination();//  combination()

  @org.jetbrains.annotations.NotNull()
  public static final @kotlin.jvm.JvmSuppressWildcards(suppress = false) OutPair<? extends Final, @kotlin.jvm.JvmSuppressWildcards() OutPair<Out<Final>, Out<@kotlin.jvm.JvmSuppressWildcards(suppress = false) ? extends Final>>> falseTrueFalse();//  falseTrueFalse()

  public static final void simpleIn(@org.jetbrains.annotations.NotNull() In<@kotlin.jvm.JvmWildcard() ? super java.lang.Object>);//  simpleIn(In<@kotlin.jvm.JvmWildcard() ? super java.lang.Object>)

  public static final void simpleOut(@org.jetbrains.annotations.NotNull() Out<@kotlin.jvm.JvmWildcard() ? extends Final>);//  simpleOut(Out<@kotlin.jvm.JvmWildcard() ? extends Final>)
}

public class Open /* Open*/ {
  public  Open();//  .ctor()
}

public final class Out /* Out*/<T>  {
  public  Out();//  .ctor()
}

public final class OutPair /* OutPair*/<Final, Y>  {
  public  OutPair();//  .ctor()
}
