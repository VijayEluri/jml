require 'buildr/bnd'

desc 'JML: Library to ease routing and transforming of JMS messages'
define 'jml' do
  project.version = '0.9.1-SNAPSHOT'
  project.group = 'org.realityforge.jml'

  compile.options.source = '1.6'
  compile.options.target = '1.6'
  compile.options.lint = 'all'
  compile.with :jms

  test.with :activemq_core, :commons_logging, :j2ee_management
  test.using :testng
  
  package(:bundle).tap do |bnd|
    bnd['Export-Package'] = "jml.*;version=#{version}"
    bnd['Import-Package'] = "javax.xml.*;resolution:=optional,*"
    bnd['Bundle-License'] = "http://www.apache.org/licenses/LICENSE-2.0.txt"
    bnd['Bundle-Vendor'] = "RealityForge.org"
    bnd['Bundle-Copyright'] = "Copyright 2010 by Peter Donald"
    bnd['Include-Resource'] = "META-INF/LICENSE=#{_('LICENSE')},META-INF/CHANGELOG=#{_('CHANGELOG')}"
    bnd['-removeheaders'] = "Include-Resource,Bnd-LastModified,Created-By,Implementation-Title,Tool"
  end
  package(:sources)
end
