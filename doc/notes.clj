;; функция с побочными эффектами
;; невозможно протестировать

(defn new-article [title body]
  {:id         (java.util.UUID/randomUUID)
   :created-at (java.time.Instant/now)
   :title      title
   :body       body})

(new-article "Lorem ipsum" "Lorem ipsum dolor sit amet, consectetur adipiscing elit...")

;; ~~~~~~~~~~~~~~~~

;; чистая фнукция, удобно тестировать, но не удобно использовать
(defn new-article [id created-at title body]
  {:id         id
   :created-at created-at
   :title      title
   :body       body})

;; да, можно воспользоваться композицией функций

(let [new-article* (partial new-article (java.util.UUID/randomUUID) (java.time.Instant/now))]
  (new-article* "Lorem ipsum" "Lorem ipsum dolor sit amet, consectetur adipiscing elit..."))

;; но представьте, что эффект вызывается на основании условия, а его вызыв дорог,
;; т.е. не имеет смысла вычислять его заранее

(defn new-article [id created-at answer-to-the-ltimate-question-of-life title body]
  {:id         id
   :created-at created-at
   :title      title
   :body       body
   :answer (if (= "The Hitchhiker's Guide to the Galaxy" title)
             answer-to-the-ltimate-question-of-life)})

;; врядли стоит ждать 10 миллионов лет, что бы получить ответ,
;; если он нужен только для единственной статьи

;; В итоге мы будем вынуждены разбивать единый процесс на множество несвязанных чистых функций,
;; в которых легко потернять тот самый процесс, ради которого все затевалось

;; ~~~~~~~~~~~~~~~~

;; "ООП" стиль. и dependency inejction через "конструктор"

(defn new-article-factory [get-id get-instant]
  (fn [title body]
    {:id         (get-id)
     :created-at (get-instant)
     :title      title
     :body       body}))

(let [get-id      #(java.util.UUID/randomUUID)
      get-instant #(java.time.Instant/now)
      new-article (new-article-factory get-id get-instant)]
  (new-article "Lorem ipsum" "Lorem ipsum dolor sit amet, consectetur adipiscing elit..."))

;; сложно упралять эффектами при тестировании
;; сложно пороверить в каком порядке вызывались эффеты, нужно использовать изменяемое состояние
;; сложно задать последовательность значений, возвращаемых различными эффектами, в нужном порядке

(let [log         (atom [])
      get-id      #(do
                     (swap! log conj :get-id)
                     (java.util.UUID/randomUUID))
      get-instant #(do
                     (swap! log conj :get-instant)
                     (java.time.Instant/now))
      new-article (new-article-factory get-id get-instant)]
  (new-article "Lorem ipsum" "Lorem ipsum dolor sit amet, consectetur adipiscing elit...")
  (assert (= [:get-id :get-instant] @log)))


;; ~~~~~~~~~~~~~~~~

;; использование контекста на базе динамических переменных (tread locals)

(declare ^:dynamic *get-id*
         ^:dynamic *get-instant*)

(defn new-article [title body]
  {:id         (*get-id*)
   :created-at (*get-instant*)
   :title      title
   :body       body})

(binding [*get-id* #(java.util.UUID/randomUUID)
          *get-instant* #(java.time.Instant/now)]
  (new-article "Lorem ipsum" "Lorem ipsum dolor sit amet, consectetur adipiscing elit..."))

;; Все те же проблемы с тестированием, только более удобное использование, т.к. нет фабрик
;; система эффектов для бедных

;; ~~~~~~~~~~~~~~~~

;; а что если вместо того, чтобы пробрасывать зависимость,
;; возвращать имя зависимости и остаток(продолжение) фукнции?
;; ведь у нас есть замыкания

(defn new-article [title body]
  [:get-id (fn [id]
             [:get-instant (fn [created-at]
                             {:id         id
                              :created-at created-at
                              :title      title
                              :body       body})])])

(let [[effect-1 continuation-1] (new-article "Lorem ipsum"
                                             "Lorem ipsum dolor sit amet...")
      _                         (assert (= :get-id effect-1))
      [effect-2 continuation-2] (continuation-1 (java.util.UUID/randomUUID))
      _                         (assert (= :get-instant effect-2))]
  (continuation-2 (java.time.Instant/now)))

;; с помощью макросов и перекомпиляции кода можно преобразовать
;; тело функции в стейт машину.
;; это позволит работать с циклами, условиями и т.п.


;; Приятным бонусом становится тот факт, что пока ожидается выполнение эффекта, функция не исполняется.
;; И нам не важно, блокирует ли вычисление эффекта поток или нет.
;; Т.е. не меняя код функции можно сделать ассинхронную обработку эффектов.
