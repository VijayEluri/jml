require 'spec'
require File.expand_path(File.dirname(__FILE__) + '/xpath_matchers.rb')

# TODO: If we are installed as a gem we should just add dependency on buildr gem and find dir based on that
DEFAULT_BUILDR_DIR=File.expand_path(File.dirname(__FILE__) + '/../../buildr')
BUILDR_DIR=ENV['BUILDR_DIR'] || DEFAULT_BUILDR_DIR

unless File.exist?("#{BUILDR_DIR}/buildr.gemspec")
  raise "Unable to find buildr.gemspec in #{BUILDR_DIR == DEFAULT_BUILDR_DIR ? 'guessed' : 'specified'} $BUILD_DIR (#{BUILDR_DIR})"
end

require 'rubygems'

# For testing we use the gem requirements specified on the buildr.gemspec
Gem::Specification.load(File.expand_path("#{BUILDR_DIR}/buildr.gemspec", File.dirname(__FILE__))).
    dependencies.each { |dep| gem dep.name, dep.requirement.to_s }


# hook into buildr's spec_helpers load process
unless defined?(SpecHelpers)
  module SandboxHook
    def SandboxHook.included(spec_helpers)
      $LOAD_PATH.unshift(File.dirname(__FILE__))
      $LOAD_PATH.unshift(File.join(File.dirname(__FILE__), '..', 'lib'))
      require 'buildr_iidea'
    end
  end

  require "#{BUILDR_DIR}/spec/spec_helpers.rb"

  module SpecHelpers
    def root_project_filename(project)
      project._("#{project.name}#{Buildr::IntellijIdea::IdeaFile::DEFAULT_SUFFIX}.ipr")
    end

    def root_project_xml(project)
      xml_document(root_project_filename(project))
    end

    def root_module_filename(project)
      project._("#{project.name}#{Buildr::IntellijIdea::IdeaFile::DEFAULT_SUFFIX}.iml")
    end

    def root_module_xml(project)
      xml_document(root_module_filename(project))
    end

    def subproject_module_filename(project, sub_project_name)
      project._("#{sub_project_name}/#{sub_project_name}#{Buildr::IntellijIdea::IdeaFile::DEFAULT_SUFFIX}.iml")
    end

    def subproject_module_xml(project, sub_project_name)
      xml_document(subproject_module_filename(project, sub_project_name))
    end

    def xml_document(filename)
      File.should be_exist(filename)
      REXML::Document.new(File.read(filename))
    end

  end

end
