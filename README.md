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

Я задумывался о том, как отделить логику приложения от деталей реализации.

Под логикой я понимаю код, описывающий принятие решений.
Это ветвление, циклы, обработка исключений. Этот код наиболее далек по стеку вызовов от ввода/вывода.
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
* Functional core, imperative shell
* [Clean architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
* [Hexagonal architecture](https://en.wikipedia.org/wiki/Hexagonal_architecture_(software))
* [Ports and adapters](http://www.dossier-andreas.net/software_architecture/ports_and_adapters.html)

Предположим, у нас есть функция, описывающая процесс входа пользователя в систему:

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

Получается, что функция `login` не чистая, т.к.  вызывает не чистые функции.
Но изначально я хотел, чтобы ядро приложения было чистым.

Подробнее об этой проблеме вы можете прочитать в статье
[Dependency rejection](https://blog.ploeh.dk/2017/02/02/dependency-rejection/).

Стоит также рассмотреть зависимости между функциями. Есть 2 типа зависимостей:
compile time и run time. Суть внедрения зависимостей в инверсии compile time зависимостей, но не run time.
Т.е. в compile time оболочка зависит (реализует неявный интерфейс) от ядра,
но в run time ядро зависит (вызывает) от оболочки.

А как сделать так, чтобы императивная оболочка вызывала чистое ядро?
И не разбивать единое вычисление на множество отдельных не связанных между собой чистых шагов?
Что, если не принимать зависимости, а возвращать описание побочного эффекта и
продолжение функции, принимающее результат исполнения этого эффекта?

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

Теперь мы можем написать императивный интерпретатор эффектов
вызывающий нашу чистую функцию и ее чистые продолжения.

# Effect

Если вы не знакомы с концепцией эффектов, то прочитайте
[Algebraic Effects for the Rest of Us](https://overreacted.io/algebraic-effects-for-the-rest-of-us/)

Бессмысленно вручную писать функции такие как `login-4`.
К счастью у нас есть макросы и мы можем писать привычный последовательный код.

```clojure
(require '[darkleaf.effect.core :as e :refer [effect with-effects !]])
```

Макрос `with-effects` делает всю работу. В местах, помеченных `!` происходит разрыв функции.
Функция `effect` показывает, что мы прерываемся на вызов эффекта.
Можно провести некоторую аналогию между `with-effects`/`!` и `async`/`await` или `go`/`<!` из  `clojure.core.async`.

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

`e/continuation` преобразует функцию с эффектами в продолжение:

```clojure
(let [cont            (e/continuation login-5)
      [effect cont-1] (cont [{:login "joe" :password "secret"}])]
  [effect (fn? cont-1)])
=> [[:get-session] true]
```

Давайте определим обработчики эффектов и запустим нашу функцию с помощью `e/perform`.

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

Выше я описывал грязные функции: `login, ->login-2, login-3`.
Чтобы их протестировать, нам потребовались бы шпионы (spies) с изменяемым состоянием:

```clojure
(require '[clojure.test :as t])
```

```clojure
(t/deftest login-test
  (let [spy-state (atom [])
        make-spy  (fn [spy-name ret]
                    (fn [& args]
                      (swap! spy-state conj {:name spy-name
                                             :args args
                                             :ret  ret})
                      ret))]
    (binding [*get-user-by-login* (make-spy :get-user-by-login {:id 1, :password-digest "digest"})
              *get-session*       (make-spy :get-session {})
              *update-session*    (make-spy :update-session :unused)
              *check-password*    (make-spy :check-password true)]
      (t/is (= {:type :processed}
               (login {:login "joe" :password "secret"})))
      (t/is (= [{:name :get-session
                 :args nil :ret {}}
                {:name :get-user-by-login
                 :args ["joe"] :ret {:id 1, :password-digest "digest"}}
                {:name :check-password
                 :args ["secret" "digest"] :ret true}
                {:name :update-session
                 :args [assoc :user-id 1] :ret :unused}]
               @spy-state)))))
```

Это довольно простой случай. Но что, если бы `login` вызывал `*get-user-by-login*` несколько раз
с разными аргументами и ожидал разные return values?

Этот тест можно считать чистым, т.к. он не изменяет окружение, хоть и имеет локальное изменяемое состояние. Но чтобы нивелировать не чистоту `login` пришлось добавить
изменяемое состояние в виде `spy-state`.

Т.к. функция `login-5` чистая, то мы можем описать последовательность запрашиваемых эффектов
с помощью неизменяемых структур данных.

```clojure
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
и какие коэффекты нужно передать обратно.
Например, если мы хотим смоделировать ситуацию, когда пользователь уже залогинен,
то мы передадим соответствующий коэффект:

```clojure
{:effect   [:get-session]
 :coeffect {:user-id 1}}
```

Сценарий проверяется функцией  `script/test`.
Подобно `t/is` она вызывает `t/do-report`.

Expected effect сравнивается с actual effect по значению с помощью `clojure.core/=`.
Также скрипт может проверять брошенные исключения с помощью `:thrown`
или обрывать проверку на заданном эффекте с помощью `:final-effect`

```clojure
{:thrown {:type    RuntimeException
          :message "Some message"
          :data    nil}}
{:final-effect [:early-return :some-value]}
```

## Stack

По аналогии с `await`, `!` может использоваться для вызова функций с эффектами или без:

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
то `!` просто вернет вычисленное значение. `(! (str "nested: " x))` как раз демонстрирует это поведение.

Вы можете использовать `!` с эффектами, функциями с эффектами, значениями и обычными функциями.

## Core analogs

По аналогии с `async`/`await`, обычная функция не может вызвать функцию с эффектами.
Например, вы не можете передавать функции с эффектами в `clojure.core/map`.
Подробности вы найдете в статье [What Color is Your Function?](https://journal.stuffwithstuff.com/2015/02/01/what-color-is-your-function/).

Есть надежда на то, что для JVM эту проблему решит [Project Loom](https://cr.openjdk.java.net/~rpressler/loom/Loom-Proposal.html). И мы забудем про макрос `with-effects`.
А пока вы можете воспользоваться функциями и макросами из
[`darkleaf.effect.core-analogs`](test/darkleaf/effect/core_analogs_test.cljc).

## Async handlers

Повторюсь, в отличие от внедрения зависимостей мы возвращаем эффект и продолжение.
Строго говоря функция уже завершилась и процессор может заниматься чем-то другим.
Это позволяет реализовать неблокирующую обработку эффектов для существующей функции с эффектами.

Для этого случая функция `e/perform` принимает дополнительные аргументы `respond` и `raise`
для передачи результата или исключения соответственно. Это работает как Clojure, так и в ClojureScript.

```clojure
(comment
  (defn perform
    ([handlers continuation coeffect-or-args])
    ([handlers continuation coeffect-or-args respond raise])))
```

Асинхронный обработчик так же должен принимать 2 дополнительных аргумента для ассинхронного случая

```clojure
(comment
  (defn my-identity-effect-handler
    ([x] x)
    ([x respond raise]
      (js/process.nextTick respond x))))
```

## Exceptions

Исключения работают так, как вы ожидаете.
Например, если обработчик бросил исключение, то поймать его можно в функции с эффектами и принять нужное решение.

```clojure
(defn catch-exception []
  (with-effects
    (try
      (! (effect :prn "Hi"))
      (catch Throwable ex
        (ex-message ex)))))
```

```clojure
(let [continuation (e/continuation catch-exception)
      handlers     {:prn (fn [_]
                           (throw (ex-info "Test" {})))}]
  (e/perform handlers continuation []))
=> "Test"
```

Чтобы протестировать обработку исключения, передайте его как coeffect:

```clojure
(t/deftest catch-exception-test
  (let [continuation (e/continuation catch-exception)
        script       [{:args []}
                      {:effect   [:prn "Hi"]
                       :coeffect (ex-info "Test" {})}
                      {:return "Test"}]]
    (script/test continuation script)))
```


С помощью `thrown` можно проверить какое исключение было брошено:

```clojure
(t/deftest throw-exception-test
  (let [ef           (fn []
                       (with-effects
                         (throw (ex-info "Message" {:foo :bar}))))
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   [:some-eff]
                       :coeffect :some-coeff}
                      {:thrown {:type    ExceptionInfo
                                :message "Message"
                                :data    {:foo :bar}}}]]
    (script/test continuation script)))
```

## Effect as value

`effect` - обычная фукнция и может использоваться отдельно от `!`.

```clojure
(t/deftest effect-as-value
  (let [effect-tag   :prn
        effect-arg   1
        test-effect  (effect effect-tag effect-arg)
        ef           (fn []
                       (with-effects
                         (! test-effect)))
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   [:prn 1]
                       :coeffect nil}
                      {:return nil}]]
    (script/test continuation script)))
```

## Higer order effect

Эффект - это значение и функции могут возвращать эффект так же как и любое другое значение.

```clojure
(t/deftest higher-order-effect
  (let [nested-ef    (fn []
                       (with-effects
                         (! (effect :a))
                         (effect :b)))
        ef           (fn []
                       (with-effects
                         (! (! (nested-ef)))))
                   ;; ----^     runs effect [:b]
                   ;; -------^  runs nested-ef
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   [:a]
                       :coeffect nil}
                      {:effect   [:b]
                       :coeffect :some-value}
                      {:return :some-value}]]
     (script/test continuation script)))
```

## Middlewares

Продолжение возвращает пару из эффекта и следующего продолжения.
Если вычисление завершено, то возвращается пара из результата и `nil`.
Таким образом, мы можем управлять вычислением.

Рассмотрим пустую middleware:

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
вы можете передавать контекст между обработчиками эффектов.
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
и должны возвращать пару из контекста и коэффекта.

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
вы можете проверять контракты функций и их эффектов/коэффектов.

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
      handlers     {:my/effect (fn [x]
                                 "wrong int")}
      continuation (-> effn
                       (e/continuation)
                       (contract/wrap-contract contract 'my/effn))]
  (try
    (e/perform handlers continuation [1])
    (catch Throwable e
      (ex-data e))))
=> {:coeffect "wrong int", :path [:my/effect :coeffect]}
```

С помощью [log middleware](test/darkleaf/effect/middleware/log_test.cljc)
вы можете вести журнал эффектов, что позволяет замораживать и продолжать вычисление.
Журнал может быть сериализован, передан на другую машину и применен для продолжения вычисления.
Вы можете начать вычисление на сервере и продолжить его в браузере и наоборот.

```clojure
(require '[darkleaf.effect.middleware.log :as log])
```

Чтобы функция прервала свое выполнения, обработчик должен вернуть особый коэффект -
`::log/suspend`.

```clojure
(def log-handlers {:my/suspend (fn [] ::log/suspend)})

(defn log-ef [x]
  (with-effects
    (+ x (! (effect :my/suspend)))))

(def log-cont-1 (-> log-ef
                    (e/continuation)
                    (log/wrap-log)))

(def log-suspended-result-1 (e/perform log-handlers log-cont-1 [1]))
```

`e/perform` вернет пару, где первый элемент сигнализирует о заморозке, а второй -
журнал выполненных эффектов и коэффектов.

```clojure
log-suspended-result-1
=> [::log/suspended [{:coeffect [1] ;; args
                      :next-effect [:my/suspend]}]]
```

Чтобы продолжить выполнение,
нужно заново проиграть выполненные ранее эффекты с помощью `log/resume`
и передать вычисленный коэффект для последнего эффекта в журнале в `e/perform`.
В этом примере последний эффект - `:my/suspend`, а в качестве коэффекта пусть будет `2`

```clojure
(def log-cont-2 (-> log-ef
                    (e/continuation)
                    (log/wrap-log)
                    (log/resume (last log-suspended-result-1))))

(def log-suspended-result-2 (e/perform log-handlers log-cont-2 2))
```

В итоге `e/perform` вернет тройку, где первый элемен сигнализирует о завершении вычисления,
второй содержит результат, а третий - весь журнал с начала вычисления.

```clojure
log-suspended-result-2
=> [::log/result
    3
    [{:coeffect [1]
      :next-effect [:my/suspend]}
     {:coeffect 2
      :next-effect 3}]]
```

Middleware можно комбинировать.
Подробнее в [composition test](test/darkleaf/effect/middleware/composition_test.cljc).

## Internals

Макрос `with-effects` использует библиотеку [cloroutine](https://github.com/leonoel/cloroutine)
для преобразования форм в стейт машину.

Как показано раньше, континуация - это обычная функция и она ожидаемо может быть вызвана много раз.
Это называется multi-shot континуацией.

```clojure
(defn login-4 [{:as form :keys [login password]}]
  [[:get-session]
   (fn [session]
     ;;...
     )])
```

Однако, сloroutine предоставляет только one-shot корутины и механизм их клонирования.
Это позволяет реализовать ожидаемое multi-shot поведение.
