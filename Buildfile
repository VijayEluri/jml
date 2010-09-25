require 'buildr_bnd'
require 'buildr_iidea'

desc 'JML: Library to ease routing and transforming of JMS messages'
define 'jml' do
  project.version = '0.0.2'
  project.group = 'realityforge'
  compile.options.source = '1.6'
  compile.options.target = '1.6'
  compile.options.lint = 'all'

  compile.with :jms
  test.with :activemq_core, :commons_logging, :j2ee_management
  test.include 'jml.LinkSuite'
  package(:bundle).tap do |bnd|
    bnd['Export-Package'] = "jml.*;version=#{version}"
  end
  package(:sources)
  package(:javadoc)
end
