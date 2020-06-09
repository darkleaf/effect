[![Clojars Project](https://img.shields.io/clojars/v/darkleaf/effect.svg)](https://clojars.org/darkleaf/effect)
[![CircleCI](https://circleci.com/gh/darkleaf/effect.svg?style=svg)](https://circleci.com/gh/darkleaf/effect)

Алгебраические эффекты для Clojure(Script).

# Api

* [core test](test/darkleaf/effect/core_test.cljc).
* [script test](test/darkleaf/effect/script_test.cljc).
* [core analogs test](test/darkleaf/effect/core_analogs_test.cljc).
* middleware
  * [composition test](test/darkleaf/effect/middleware/composition_test.cljc).
  * [context test](test/darkleaf/effect/middleware/context_test.cljc).
  * [contract test](test/darkleaf/effect/middleware/contract_test.cljc).
  * [log test](test/darkleaf/effect/middleware/log_test.cljc).
  * [reduced test](test/darkleaf/effect/middleware/reduced_test.cljc).

# Rationale

Я здумывался о том, как отделить логику приложения от деталей реализации.

Под логикой я понимаю код, описывающий принятие решений.
Это ветвление, циклы, обработка исключений. Этот код наиболее далек по стеку вызыов от ввода/вывода.
Этот код написан в функциональной парадигме.

Под деталями реализации я понимаю код, взаимодействующий с внешним миром, наиболее близкий к вводу/выводу.
Этот код максимально прямолинейный, написанный в императивной парадигме.

Имея такое разделение, становится возможным начинать разработку с высокоуровневой логики,
дешево проверять гипотезы, находить противоречия в функциональных требованиях.

Отмечу, что это разделение происходит только на уровне кода.
Вы по прежнему должны задумываться о деталях.
Но только задумываться, а не реализовывать их в начале работы, когда требования постоянно меняются.
Сможет ли какая-либо база данных исполнить задуманный запрос?
Сможет ли сторонний сервис выдержать в будущем создаваемую нами нагрузку?
От куда мы будем получать все необходимые данные?

Эта идея перекликается с
* functional core imperative shell
* clean architecture
* ports and adapters

Однако теория разбивается о реальность.


Предположим, у нас есть функция, описывающая логин пользователя:

```clojure
(declare ^:dynamic *get-session*
         ^:dynamic *update-session*
         ^:dynamic *get-user-by-login*
         ^:dynamic *check-password*)

(defn login [{:as form :keys [login password]}]
  (let [session (*get-session*)]
    (if (contains? session :user-id)
      {:type :already-logged-in}
      (let [{:as user :keys [id password-digest]} (*get-user-by-login* login)]
        (if (or (nil? user)
                (not (*check-password* password password-digest)))
          {:type :wrong-login-or-password}
          (do
            (*update-session* assoc :user-id id)
            {:type :processed}))))))
```

Функции  вроде `*get-user-by-login*` - зависимости, детали реализации.
С помощью `bindings` можно установить заглушку и протестировать логику
до появления промышленной реализации зависимостей.

Внедрение зависимостей можно реализовать и через
статические переменные и `with-redefs`, замыкания или передачу контекста:

```clojure
(defn ->login-2 [get-session update-session get-user-by-login check-password]
  (fn login [form]))

(defn login-3 [ctx form])
```

Можно ошибочно предположить, что функция `login` чистая, ведь она не вызывает побочные эффекты.
Однако зависимости, по своей природе, взаимодействуют с вводом/выводом.
Вы же хотите получать данные пользователя из базы данных?

Давайте для наглядности рассмотрим сигнатуру функции `->login` в haskell нотации

```haskell
build_login_2 :: IO SessionData ->
                 ((SessionData -> SessionData) -> IO SessionData)
                 (String -> IO UserData)
                 (String -> String -> IO Boolean)
                 FormData -> ResponseData
```

Получается, что функция `login` не чистая, т.к.  вызывает не чистые фукнции.
Но изначально я хотел, чтобы ядро приложения было чистым.

Подробнее об этой проблеме вы можете прочитать в статье
[Dependency rejection](https://blog.ploeh.dk/2017/02/02/dependency-rejection/).

Как сделать так, чтобы императивная оболочка вызывала чистое ядро?
И не разбивать единое вычисление на множество отедельных не связанных между собой чистых шагов?
Что, если не принимать зависимости, а возвращать описание побочного эффекта и продолжение функции?

```clojure
(defn login-4 [{:as form :keys [login password]}]
  [[:get-session]
   (fn [session]
     ;;...
     [[:get-user-by-login login]
      (fn [{:as user :keys [id password-digest]}]
        ;; ...
        [[:check-password password password-digest]
         (fn [correct-password?]
           ;; ...
           [[:update-session assoc :user-id id]
            (fn [session]
              ;; ...
              )])])])])
```

Т.е. `login-4` возвращает `[effect-description continuation-1]`,
первое продолжение в свою очередь возвращает эффект и второе продолжение.

Теперь мы можем написать императивный интерпретатор эффектов
вызывающий нашу чистую функцию и ее чистые продолжения.

# Effect

Если вы не знакомы с концепцией эффектов, то прочитайте
[Algebraic Effects for the Rest of Us](https://overreacted.io/algebraic-effects-for-the-rest-of-us/)

Функция `login-4` выглядит устрашающе и порождает "callback hell'.
К счастью у нас есть макросы и мы можем скрыть эту деталь от пользователя.

```clojure
(require '[darkleaf.effect.core :as e :refer [effect with-effects !]])
```

Макрос `with-effects` делает всю работу. В местах, помеченных `!` происходит разрыв фукнции.
Фукнция `effect` показывает, что мы прерываемся на вызов эффекта, а не другой фукнции.
Можно провести некоторую аналогию между `with-effects/!` и `async/await` или `core.async`.

```clojure
(defn login-5 [{:as form :keys [login password]}]
  (with-effects
    (let [session (! (effect :get-session))]
      (if (contains? session :user-id)
        {:type :already-logged-in}
        (let [{:as user :keys [id password-digest]} (! (effect :get-user-by-login login))]
          (if (or (nil? user)
                  (not (! (effect :check-password password password-digest))))
            {:type :wrong-login-or-password}
            (do
              (! (effect :update-session assoc :user-id id))
              {:type :processed})))))))
```

```clojure
(let [cont            (e/continuation login-5)
      [effect cont-1] (cont [{:login "joe" :password "secret"}])]
  [effect (fn? cont-1)])
=> [[:get-session] true]
```

Давайте определим обработчики эффектов и запустим нашу фукнцию с помощью `e/perform`.

```clojure
(let [handlers {:get-session       (fn [] {})
                :update-session    (fn [& _] :unused)
                :get-user-by-login (fn [login] {:id              1
                                                :password-digest "digest"})
                :check-password    (fn [password digest] true)}
      cont     (e/continuation login-5)]
  (e/perform handlers cont [{:login "joe" :password "secret"}]))
=> {:type :processed}
```

## Script testing

Такой подход плохо подходит для тестирования, поэтому давайте протестируем функцию с использованием сценария:

```clojure
(require '[clojure.test :as t])
(require '[darkleaf.effect.script :as script])
```

```clojure
(t/deftest login-5-test
  (let [cont   (e/continuation login-5)
        script [{:args [{:login "joe" :password "secret"}]}
                {:effect   [:get-session]
                 :coeffect {}}
                {:effect   [:get-user-by-login "joe"]
                 :coeffect {:id 1 :password-digest "digest"}}
                {:effect   [:check-password "secret" "digest"]
                 :coeffect true}
                {:effect   [:update-session assoc :user-id 1]
                 :coeffect :unused}
                {:return {:type :processed}}]]
    (script/test cont script)))
```

Сценарий проверят какие и в каком порядке были запрошены эффекты
и какие коэффекты нужно передать в обратно программу.
Expected effect сравнивается с actual effect по значению с помощью `clojure.core/=`.
Также скрипт может проверять брошенные исключения с помощью `:thrown`
или обрывать проверку на заданном эффекте с помощью `:final-effect`

```clojure
{:thrown {:type   RuntimeException
          :mssage "Some message"
          :data   nil}}
{:final-effect [:early-return :some-value]}
```

## Stack

Функция с эффектами может вызывать другую функцию с эффектами или без

```clojure
(t/deftest stack-use-case
  (let [nested-ef    (fn [x]
                       (with-effects
                         (! (effect :prn :nested "start"))
                         (! (effect :prn :nested x))
                         (! (str "nested: " x))))
        ef           (fn [x]
                       (with-effects
                         (! (effect :prn :main "start"))
                         (! (nested-ef x))))
        continuation (e/continuation ef)
        script       [{:args ["arg"]}
                      {:effect   [:prn :main "start"]
                       :coeffect nil}
                      {:effect   [:prn :nested "start"]
                       :coeffect nil}
                      {:effect   [:prn :nested "arg"]
                       :coeffect nil}
                      {:return "nested: arg"}]]
    (script/test continuation script)))
```

`(! (nested-ef x))` - вызов функции с эффектами.
Если `nested-ef` перестанет использовать макрос `with-effects`,
то `!` просто вернет вычисленное значение.

Вы можете использовать `!` с эффектами, функциями с эффектами, значениями и обычными фукнциями.

## Core analogs

По аналогии с async/await поддержка эффектов делит функции на "цвета".
Подробности вы найдете в статье [What Color is Your Function?](https://journal.stuffwithstuff.com/2015/02/01/what-color-is-your-function/).
Т.е. обычная функция не может вызвать фукнцию с эффектами.
Например, вы не можете передавать функции с эффектами в `clojure.core/map`.
Есть надежда на то, что для JVM эту проблему решит [Project Loom](https://cr.openjdk.java.net/~rpressler/loom/Loom-Proposal.html).
Но пока вы можете воспользоваться фукнциями и макросами из
[`darkleaf.effect.core-analogs`](test/darkleaf/effect/core_analogs_test.cljc).

## Middlewares

Продолжение возвращает пару из эффекта и следующего продолжения.
Если вычисление завершено, то возвращается пара из результата и `nil`.
Таким образом, мы можем управлять вычислением.

Рассмотрим пустую middlware:

```clojure
(defn wrap-blank [continuation]
  (when (some? continuation)
    (fn [coeffect]
      (let [[effect continuation] (continuation coeffect)]
        [effect (wrap-blank continuation)]))))

(let [continuation (-> login-5
                       (e/continuation)
                       (wrap-blank))]
    #_"some code")
```

С помощью [context middleware](test/darkleaf/effect/middleware/context_test.cljc)
вы можете передавать контест между обработчиками эффектов.
Это напоминает монады State, Reader и Writer.

```clojure
(require '[darkleaf.effect.middleware.context :as context])
```

```clojure
(let [ef           (fn [arg]
                     (with-effects
                       (! (effect :ctx/update inc))
                       (! (effect :ctx/update + 2))
                       arg))
      continuation (-> ef
                       (e/continuation)
                       (context/wrap-context))
      handlers     {:ctx/update (fn [context f & args]
                                  (let [new-context (apply f context args)]
                                    [new-context new-context]))}]
    (e/perform handlers continuation [0 [:some-arg]]))
=> [3 :some-arg]
```

После применения `context/wrap-context` обработчики принимают контекст первым дополнительным аргументом
и должны возвращать пару из контекта и коэффекта.

С помощью [reduced middleware](test/darkleaf/effect/middleware/reduced_test.cljc)
вы можете досрочно прервать вычисление. Это напоминает монады Maybe или Either.

```clojure
(require '[darkleaf.effect.middleware.reduced :as reduced])
```

```clojure
(let [ef           (fn [x]
                     (with-effects
                       (+ 5 (! (effect :maybe x)))))
      handlers     {:maybe (fn [value]
                             (if (nil? value)
                               (reduced nil)
                               value))}
      continuation (-> ef
                       (e/continuation)
                       (reduced/wrap-reduced))]
  [(e/perform handlers continuation [1])
   (e/perform handlers continuation [nil])])
=> [6 nil]
```

Если обработчик возвращает `reduced` значение, то вычисление прерывается и это значение
используется для возврата из функции.

С помощью [contract middleware](test/darkleaf/effect/middleware/contract_test.cljc)
вы можете проверять контракты фукнций и их эффектов/коэффектов.

```clojure
(require '[darkleaf.effect.middleware.contract :as contract])
```

```clojure
(let [effn         (fn [x]
                     (with-effects
                       (+ x (! (effect :my/effect 1)))))
      contract     {'my/effn   {:args   (fn [x] (int? x))
                                :return int?}
                    :my/effect {:effect   (fn [x] (int? x))
                                :coeffect int?}}
      continuation (-> effn
                       (e/continuation)
                       (contract/wrap-contract contract 'my/effn))])
```

С помощью [log middleware](test/darkleaf/effect/middleware/log_test.cljc)
вы можете вести журнал эффектов, что позволяет замораживать и продолжать вычисление.
Журнал может быть сериализован, передан на другую машину и применен для продолжения вычисления.
Вы можете начать вычисление на сервере и продолжить его в браузере и наоброт.

Middlware можно объединять. Подробнее в [composition test](test/darkleaf/effect/middleware/composition_test.cljc).

## Async handlers

Вы можете писать код с эффектами, синхронно его тестировать, но запускать с ассинхронными обработчиками.
Для этого случая функция `e/perform` принимает доплнительные агрументы `respond` и `raise`.

```clojure
(comment
  (defn perform
    ([handlers continuation coeffect-or-args])
    ([handlers continuation coeffect-or-args respond raise])))
```

Ассинхронный обработчик так же принимать 2 дополнительных агрумента: `respond` и `raise`

```clojure
(comment
  (defn my-effect-handler
    ([arg-1 arg-2])
    ([arg-1 arg-2 respond raise])))
```

## Internals

https://github.com/leonoel/cloroutine
