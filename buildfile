require 'buildr/git_auto_version'
require 'buildr/jacoco'
require 'buildr/bnd'
require 'buildr/gpg'
require 'buildr/custom_pom'

PROVIDED_DEPS = [:javax_jms, :javax_ejb, :javax_annotation]
TEST_DEPS = [:activemq_core, :commons_logging, :j2ee_management]

desc 'JML: Library to ease routing and transforming of JMS messages'
define 'jml' do
  project.group = 'org.realityforge.jml'

  compile.options.source = '1.6'
  compile.options.target = '1.6'
  compile.options.lint = 'all'
  compile.with PROVIDED_DEPS

  pom.add_apache_v2_license
  pom.add_github_project("realityforge/guiceyloops")
  pom.add_developer('realityforge', "Peter Donald")
  pom.provided_dependencies.concat PROVIDED_DEPS

  test.with TEST_DEPS
  test.using :testng

  package(:bundle).tap do |bnd|
    bnd['Export-Package'] = "org.realityforge.jml.*;version=#{version}"
    bnd['Import-Package'] = "javax.xml.*;resolution:=optional,*"
    bnd['Bundle-License'] = "http://www.apache.org/licenses/LICENSE-2.0.txt"
    bnd['Bundle-Vendor'] = "RealityForge.org"
    bnd['Bundle-Copyright'] = "Copyright 2010 by Peter Donald"
    bnd['Include-Resource'] = "META-INF/LICENSE=#{_('LICENSE')},META-INF/CHANGELOG=#{_('CHANGELOG')}"
    bnd['-removeheaders'] = "Include-Resource,Bnd-LastModified,Created-By,Implementation-Title,Tool"
  end
  package(:sources)
  package(:javadoc)
end
