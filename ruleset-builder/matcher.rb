# Matches alternative version checks and normalizes all the checks in the form "<=" or "!="
# Input:
#   - 1: input file (merged)
#   - 2: output file
# Output:
#   - Writes the result to (2)

require "csv"

raise "Specify input/output file"   if ARGV.size != 2

INFILE, OUTFILE = *ARGV

raise "Output file already exists"      if FileTest.exists? OUTFILE
raise "Input file does not exist"       unless FileTest.exists? INFILE


class App
    attr_accessor   :name
    attr_accessor   :version
    attr_accessor   :sdkmin
    attr_accessor   :methods
    
    def initialize(name, version, sdkmin)
        @name       = name
        @version    = version
        @sdkmin     = sdkmin
        @methods    = {}
    end
end

class AppMethod
    attr_accessor   :app_name
    attr_accessor   :signature
    attr_accessor   :checks
    
    def initialize(signature, app_name="")
        @signature  = signature
        @checks     = []
        @app_name   = app_name
    end
    
    def matched_checks
        matched = {}
        matches = []
        for i in 0...(checks.size-1)
            next if matched[i]
            for j in 1...checks.size
                next if matched[j]
                if checks[i].checker == checks[j].checker.inverse
                    matched[i] = true
                    matched[j] = true
                    
                    matches.push MatchedCheck.new(checks[i], checks[j])
                    break
                end
            end
        end
        
        if matched.size != checks.size
            if checks.size % 2 != 0
                for i in 0...checks.size
                    if !matched[i]
                        matches.push MatchedCheck.new(checks[i], UnmatchedCheck.new(checks[i].checker.inverse.to_s, [], "", 0))
                    end
                end
            else
                warn "Inconsistent checks @ #@app_name:#{signature} "
            end
        end
        return matches
    end
end

class UnmatchedCheck
    attr_accessor   :checker
    attr_accessor   :apis
    attr_accessor   :message
    attr_accessor   :modified_files
    
    def initialize(checker_string, apis, message, modified_files)
        @checker    = Checker.new(checker_string)
        @apis       = apis
        @message    = message
        @modified_files = modified_files
    end
end

class Checker
    attr_reader     :type
    attr_reader     :version
    
    def initialize(checker)        
        @type, @version = *checker.scan(/SDK\_INT\s*([<>!=]=?)\s*([0-9]+)/).flatten
        @version = @version.to_i
    end
    
    def inverse
        case @type
        when ">="
            return Checker.new("SDK_INT < #@version")
        when ">"
            return Checker.new("SDK_INT <= #@version")
        when "<="
            return Checker.new("SDK_INT > #@version")
        when "<"
            return Checker.new("SDK_INT >= #@version")
        when "!="
            return Checker.new("SDK_INT == #@version")
        when "=="
            return Checker.new("SDK_INT != #@version")
        end
    end
    
    def inverse!
        case @type
        when ">="
            @type = "<"
        when ">"
            @type = "<="
        when "<="
            @type = ">"
        when "<"
            @type = ">="
        when "!="
            @type = "=="
        when "=="
            @type = "!="
        end
    end
    
    def ==(oth)
        return false unless oth.is_a? Checker
        return oth.type == self.type && oth.version == self.version
    end
    
    def to_s
        return "SDK_INT #@type #@version"
    end
end

class MatchedCheck
    attr_accessor   :type
    attr_accessor   :version
    attr_accessor   :true_apis
    attr_accessor   :false_apis
    attr_accessor   :message
    attr_accessor   :modified_files
    
    def initialize(unmatched1, unmatched2)
        if ["==", "!="].include? unmatched1.checker.type
            equals      = unmatched1.checker.type == "==" ? unmatched1 : unmatched2
            unequals    = unmatched1.checker.type == "!=" ? unmatched1 : unmatched2
            
            self.type       = "!="
            self.version    = unequals.checker.version
            self.true_apis  = unequals.apis
            self.false_apis = equals.apis
        elsif [unmatched1.checker.type, unmatched2.checker.type].include? "<="
            le  = unmatched1.checker.type == "<=" ? unmatched1 : unmatched2
            gt  = unmatched1.checker.type == ">"  ? unmatched1 : unmatched2
            
            self.type       = "<="
            self.version    = le.checker.version
            self.true_apis  = le.apis
            self.false_apis = gt.apis
        else
            lt  = unmatched1.checker.type == "<"  ? unmatched1 : unmatched2
            ge  = unmatched1.checker.type == ">=" ? unmatched1 : unmatched2
            
            self.type       = "<="
            self.version    = lt.checker.version - 1
            self.true_apis  = lt.apis
            self.false_apis = ge.apis
        end
        
        # They are always the same for both the unmatched checks
        self.message = unmatched1.message
        self.modified_files = unmatched1.modified_files
    end
end

dataset = {}
CSV.parse(File.read(INFILE), headers: true, col_sep: ",") do |row|
    check   = row["check"]
    api     = row["apis"]
    app     = row["app"]
    version = row["version"]
    sdkmin  = row["sdk_min"]
    method  = row["method"]
    
    apis = api.to_s.split("&").sort
    
    dataset[app] = {}                                       unless dataset[app]
    dataset[app][version] = App.new(app, version, sdkmin)   unless dataset[app][version]
    
    real_app = dataset[app][version]
    real_app.methods[method] = AppMethod.new(method, app)           unless real_app.methods[method]
    
    real_app.methods[method].checks.push UnmatchedCheck.new(check, api.to_s.split("&"), row["message"], row["modified_files"])
end

class Array
    def set_equals(oth)
        return false unless oth.is_a? Array
        return false if oth.size != self.size
        
        diff1 = oth - self
        diff2 = self - oth
        
        return diff1.size == 0 && diff2.size == 0
    end
end

CSV.open OUTFILE, "w" do |csv|
    csv << ["app", "version", "sdk_min", "method", "type", "checked_version", "true_apis", "false_apis", "only_in_true_apis", "only_in_false_apis", "message", "modified_files"]
    dataset.values.each do |versions|
        versions.values.each do |app|
            puts "Matching for #{app.name}@#{app.version}"
            app.methods.values.each do |method|
                method.matched_checks.each do |matched|
                    next if matched.true_apis.size == 0 && matched.false_apis.size == 0
                    next if matched.true_apis.set_equals(matched.false_apis)
                    csv << [
                        app.name, app.version, app.sdkmin,
                        method.signature, 
                        matched.type, matched.version, 
                        matched.true_apis.join("&"), 
                        matched.false_apis.join("&"),
                        (matched.true_apis - matched.false_apis).join("&"),
                        (matched.false_apis - matched.true_apis).join("&"),
                        matched.message,
                        matched.modified_files
                    ]
                end
            end
        end
    end
end
