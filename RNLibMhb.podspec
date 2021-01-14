require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = "RNLibMhb"
  s.version      = package['version']
  s.summary      = package['description']
  s.license      = package['license']

  s.authors      = package['author']
  s.homepage     = package['homepage']
  s.author       = { "author" => "developer@cofit.me" }
  s.platform     = :ios, "9.0"
  s.source       = { :git => "https://github.com/Cofit/react-native-lib-mhb.git", :tag => "master" }
  s.source_files = "ios/**/*.{h,m,swift}"
  s.requires_arc = true
  s.vendored_frameworks = "ios/MHBSdk.framework", "ios/Alamofire.framework"
  s.private_header_files = "ios/MHBSdk.framework/Headers/*.h", "ios/Alamofire.framework/Headers/*.h"
  s.dependency "React"
  s.dependency "SSZipArchive"
  s.dependency "CryptoSwift", '~> 1.3.8'
end
