module Buildr
  module OSGi
    class Runtime
      attr_accessor :container_type
      attr_reader :project

      def initialize(project)
        @project = project
        @features = {}
        @container_type = :felix
      end

      def container
        unless @container
          container_factory_method = "create_#{container_type}_container".to_sym
          raise "Container type #{container_type} not supported" unless self.respond_to? container_factory_method
          @container = self.send(container_factory_method)
        end
        @container
      end

      def enable_feature(feature)
        if feature.is_a? Symbol
          add_feature( create_feature(feature) )
        elsif feature.is_a? Feature
          add_feature(feature)
        else
          raise "Feature must be a symbol or an instance of Feature"
        end
      end

      def features
        @features.values
      end

      def system_bundles
        self.container.bundles + self.features.collect {|f| f.bundles }.flatten
      end

      def application_bundles
        @application_bundles ||= []
      end

      def bundles
        system_bundles + application_bundles 
      end

      def include_bundles(*specs)
        options = {:run_level => Bundle::DEFAULT_RUN_LEVEL}
        options.merge!( specs.pop.dup ) if Hash === specs.last
        Buildr.artifacts(specs).each do |artifact|
          name = artifact.respond_to?(:to_spec) ? artifact.to_spec : artifact.to_s
          self.application_bundles << Bundle.new(name, options[:run_level])
        end
      end

      protected

      def add_feature(feature)
        raise "Feature #{feature.feature_key} already defined" if @features[feature.feature_key]
        @features[feature.feature_key] = feature
      end

      def create_feature(feature_key)
        bundles_factory_method = "define_#{feature_key}_bundles".to_sym
        if self.respond_to? bundles_factory_method
          f = Feature.new(feature_key)
          f.bundles = self.send(bundles_factory_method)
          return f
        else
          feature_factory_method = "define_#{feature}_feature".to_sym
          raise "Feature #{feature} not supported" unless self.respond_to? feature_factory_method
          f = self.send(feature_factory_method)
          if f.feature_key != feature
            raise "Factory method define_#{feature}_feature for feature #{feature} created a feature with key #{f.feature_key} rather than #{feature}"
          end
          return f
        end
      end
    end
  end
end