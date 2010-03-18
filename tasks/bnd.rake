# Provides tasks to use bnd with buildr.  Of particular interest is 
# bnd:wrap, which implements automatic bundling of buildr-packaged jars
# if enabled for a project.

module Bnd
  class << self
    def libraries
      ["biz.aQute:bnd:jar:0.0.384"]
    end

    def bnd_main(*args)
      Java.load
      if Java.aQute.bnd.main.bnd.main(args.to_java(Java.java.lang.String)) != 0
        fail "Failed to run Bnd, see errors above."
      end          
    end

    def bnd_filename_from_jar(filename)
      filename.sub /(\.jar)?$/, '.bnd'
    end
  end

  include Buildr::Extension

  def leaf_project_name
    if self.parent
      return self.name[self.parent.name.size + 1, self.name.length]
    else
      return self.name
    end
  end

  def package_as_bundle(filename)
    dirname = File.dirname(filename)
    bnd_filename = Bnd.bnd_filename_from_jar( filename )

    directory( dirname )

    # TODO: Determine why Buildr.application.buildfile is a dependency
    project.file(bnd_filename => [Buildr.application.buildfile, dirname]) do |task|
      File.open(task.name, 'w') do |f|
        project.bnd.output = filename 
        project.bnd.write(f)
      end
    end

    project.file( filename => [bnd_filename] ) do |task|
      Bnd.bnd_main( bnd_filename )
    end

    project.task('bnd:print' => [filename]) do |task|
      Bnd.bnd_main( filename )
    end
  end

  def package_as_bundle_spec(spec)
    # Change the source distribution to .jar extension
    spec.merge( :type => :jar )
  end

  first_time do
    Java.classpath << Bnd.libraries
    desc "Does `bnd print` on the packaged jar and stdouts the output for inspection"
    Project.local_task("bnd:print")
  end

  def bnd
    @bnd ||= ProjectBndProperties.new(self)
  end

  module BndProperties
    BND_TO_ATTR = {
        '-output' => :output,
        '-classpath' => :classpath,
        'Bundle-Version' => :version,
        'Bundle-SymbolicName' => :symbolic_name,
        'Bundle-Name' => :name,
        'Bundle-Description' => :description,
        'Import-Package' => :import_packages_serialized,
        'Export-Package' => :export_packages_serialized
    }
    LIST_ATTR = BND_TO_ATTR.values.select { |a| a.to_s =~ /_serialized$/ }
    SCALAR_ATTR = BND_TO_ATTR.values - LIST_ATTR

    # Scalar properties are deliberately not memoized to allow
    # the default values to be evaluated as late as possible.

    SCALAR_ATTR.each do |attribute|
      class_eval <<-RUBY
        def #{attribute}
          @#{attribute} || (default_#{attribute} if respond_to? :default_#{attribute})
        end
      RUBY
    end

    attr_writer(*SCALAR_ATTR)
    attr_writer :autostart

    def autostart?
      @autostart.nil? ? true : @autostart
    end

    # List properties are memoized to allow for concatenation via the 
    # read accessor.

    LIST_ATTR.each do |attribute_ser|
      attribute = attribute_ser.to_s.sub(/_serialized$/, '')
      class_eval <<-RUBY
        def #{attribute}
          @#{attribute} ||= (self.respond_to?(:default_#{attribute}) ? default_#{attribute} : [])
        end
        
        def #{attribute_ser}
      #{attribute}.join(', ')
        end
        
        def #{attribute_ser}=(s)
          # XXX: this does not account for quotes
          @#{attribute} = s.split(/\\s*,\\s*/)
        end
      RUBY
    end

    def write(f)
      f.print self.to_hash.collect { |k, v| "#{k}=#{v}" }.join("\n")
    end

    def to_hash
      Hash[ *BND_TO_ATTR.keys.collect { |k| [ k, self[k] ] }.reject { |k, v| v.nil? || v.empty? }.flatten ].merge(other)
    end

    def [](k)
      if BND_TO_ATTR.keys.include?(k)
        self.send BND_TO_ATTR[k]
      else
        other[k]
      end
    end

    def []=(k, v)
      if BND_TO_ATTR.keys.include?(k)
        self.send :"#{BND_TO_ATTR[k]}=", v
      else
        other[k] = v
      end
    end

    def merge!(other)
      other.each do |k, v|
        self[k] = v
      end
      self
    end

    protected

    def other
      @other ||= { }
    end
  end

  class ProjectBndProperties
    include BndProperties

    def initialize(project)
      @project = project
    end

    def default_version
      project.version
    end

    def default_classpath
      ([project.compile.target] + project.compile.dependencies).collect(&:to_s).join(", ")
    end

    def default_symbolic_name
      [project.group, project.id].join('.')
    end

    def default_description
      project.full_comment
    end

    def default_import_packages
      ['*']
    end

    def default_export_packages
      ["*"]
    end

    protected

    def project
      @project
    end
  end
end unless Object.const_defined?(:Bnd)

class Buildr::Project
  include Bnd
end