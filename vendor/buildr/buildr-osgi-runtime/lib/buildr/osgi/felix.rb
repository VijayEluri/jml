module Buildr
  module OSGi
    class Felix
      def bundles
        [
            Bundle.new("org.apache.felix:org.apache.felix.main:jar:2.0.4", 1)
        ]        
      end

      def configuration_dir
        "conf"
      end

      def bundle_dir
        "bundles"
      end

      def system_bundle_repository
        "system"
      end
    end

    class Runtime
      protected

      def create_felix_container
        Felix.new
      end
    end
  end
end