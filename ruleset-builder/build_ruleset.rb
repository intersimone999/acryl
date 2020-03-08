# Aggregates the matched file, measuring reliability (#apps, excluding library repetitions) and #occurrences for each check
# Input:
#   - 1: input file (matched)
#   - 2: output file
# Output:
#   - Writes the result to (2)


require "csv"

raise "Specify input/output file and stopword file"   if ARGV.size != 3

INFILE, OUTFILE, STOPWORD_FILE = *ARGV

raise "Output file already exists"      if FileTest.exists? OUTFILE
raise "Input file does not exist"       unless FileTest.exists? INFILE
raise "Stopword file does not exist"    unless FileTest.exists? STOPWORD_FILE

STOPWORDS = File.read(STOPWORD_FILE).split("\n")

class MethodRef
    attr_accessor   :name
    attr_accessor   :app
    attr_accessor   :message
    attr_accessor   :modified_files
    
    def initialize(method, app, message, modified_files)
        @name = method
        @app = app
        @message = message
        @modified_files = modified_files
    end
    
    def clean_message
        m = @message.clone
        m.gsub! /([a-z])([A-Z])/, '\1 \2'
        m.downcase!
        m.gsub! /[^A-Za-z]/, " "
        m.gsub! /\s+/, " "
        
        return m
    end
    
    def ==(b)
        return self.name == b.name
    end
end

dataset = {}
CSV.parse(File.read(INFILE), {headers: true, col_sep: ","}) do |row|
    check       = row["type"] + " " + row["checked_version"]
    true_apis   = row["true_apis"]
    false_apis  = row["false_apis"]
    app         = row["app"]
    method      = row["method"]
    message     = row["message"]
    modified_files = row["modified_files"]
        
    dataset[check] = {} unless dataset[check]
    dataset[check][[true_apis,false_apis]] = {occurrences: 0, involved_methods: []} unless dataset[check][[true_apis,false_apis]]
    
    dataset[check][[true_apis,false_apis]][:occurrences] += 1
    
    method_ref = MethodRef.new(method, app, message, modified_files)
    dataset[check][[true_apis,false_apis]][:involved_methods].push method_ref unless dataset[check][[true_apis,false_apis]][:involved_methods].include?(method_ref)
end

def compute_keywords(data)
    data[:involved_methods].each do |involved_method|        
        if involved_method.message
            message = involved_method.clean_message
            if message.length > 0
                message.split(/\s+/).each do |word|
                    keywords[word] = {} unless keywords[word]
                    keywords[word][involved_method.app] = 0.0 unless keywords[word][involved_method.app]
                    
                    if involved_method.modified_files.to_f != 0
                        # Sums +1 (changes involved) and a bonus related to the probability that such a keyword is about the rule (in (0, 1]
                        keywords[word][involved_method.app] += 1.0 + 1.0 / involved_method.modified_files.to_f
                    end
                end
            end
        end
    end
    
    # Remove keywords appearing in less than 2 commit messages
    keywords.delete_if {|a| a.length <= 1 || STOPWORDS.include?(a) || keywords[a].values.sum < 2}
    keywords = keywords.sort_by {|a| [a[1].size, a[1].values.sum]}.reverse
    
    return keywords
end

CSV.open OUTFILE, "w" do |csv|
    csv << ["comparison", "version", "true_apis", "false_apis", "occurrences", "napps", "reliability", "reliability_direct", "reliability_inverse", "keywords", "appset"]
    dataset.each do |check, apival|
        apival.each do |api, data|
            true_apis, false_apis = *api
            comparison, version = *check.scan(/([<=>!]=?)\s*(-?[0-9]+)/).flatten
            
            unique_apps = []
            
            final_message = ""
            keywords = {}
            data[:involved_methods].each do |involved_method|
                unique_apps.push involved_method.app unless unique_apps.include? involved_method.app
            end
                        
            ####### Keyword-based message #######
            # - 3.0 because it is the minimum value for a keyword (2 changes -previous filter- in 1 app with no bonus)
            #final_message = keywords.map {|w| w[0] + "(#{w[1].size + w[1].values.sum - 3.0})"}.join(" ")

            ####### Most-focused message #######
            messages = []
            data[:involved_methods].each do |involved_method|
                if involved_method.message
                    if involved_method.message.length > 0
                        messages.push [involved_method.message, involved_method.modified_files.to_f]
                    end
                end
            end
            messages.push ["", 100000000]
            messages.delete_if {|m| m[1] == 0}
            selected = messages.sort_by {|m| [m[1], -m[0].length]}[0]
            
            final_message = selected[0]
            final_message += " (among: #{messages.size}, score: #{1.0 / selected[1]})" if final_message.length > 0 
            
            final_message = ""
            messages.uniq.each do |m|
                final_message += m[0]
                final_message += " (among: #{messages.size}, score: #{1.0 / m[1]})" if final_message.length > 0 
                final_message += " ||| "
            end
            
            csv << [comparison, version, true_apis, false_apis, data[:occurrences], unique_apps.size, unique_apps.size, unique_apps.size, unique_apps.size, final_message, unique_apps.join("&")]
        end
    end
end
