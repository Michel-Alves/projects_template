; https://github.com/technomancy/leiningen/blob/stable/sample.project.clj

(defproject {{tld}}.{{author}}/{{app_name}} "{{version}}"
  :description "{{description}}"
  :license {:name "{{license_name}}"
            :url "{{license_url}}" }
  :dependencies [[org.clojure/clojure "{{clojure_version}}"]]
  :repl-options {:init-ns {{app_name}}.core})
