gem 'buildr-bnd', :version => '0.0.3'
gem 'buildr-iidea', :version => '0.0.7'

require 'buildr_bnd'
require 'buildr_iidea'

repositories.remote << 'https://repository.apache.org/content/repositories/releases'
repositories.remote << 'http://www.ibiblio.org/maven2' # For ECJ ... urgh
repositories.remote << Buildr::Bnd.remote_repository

JMS = 'org.apache.geronimo.specs:geronimo-jms_1.1_spec:jar:1.1.1'
AMQ = ['org.apache.activemq:activemq-core:jar:5.3.2', 'commons-logging:commons-logging:jar:1.1', 'org.apache.geronimo.specs:geronimo-j2ee-management_1.0_spec:jar:1.0']

desc 'JAva Message EXchange is an osgi based jms router in it' 's infancy'
define 'jml' do
  project.version = '0.1.1-SNAPSHOT'
  project.group = 'jamex'
  compile.options.source = '1.6'
  compile.options.target = '1.6'
  compile.options.lint = 'all'

  compile.with JMS
  test.with AMQ
  test.include 'jml.LinkSuite'
  package(:bundle).tap do |bnd|
    bnd['Export-Package'] = "jml.*;version=#{version}"
  end
  package(:sources)
  package(:javadoc)
end
