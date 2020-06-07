[![Clojars Project](https://img.shields.io/clojars/v/darkleaf/effect.svg)](https://clojars.org/darkleaf/effect)
[![CircleCI](https://circleci.com/gh/darkleaf/effect.svg?style=svg)](https://circleci.com/gh/darkleaf/effect)

# Effect

Алгебраические эффекты для Clojure(Script).

## Rationale

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
                (*check-password* password password-digest))
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
      (fn [user]
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













## Development

```
lein test
lein doo node node-none once
lein doo node node-advanced once
```
