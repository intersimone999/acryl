# Aggregates the matched file, measuring reliability (#apps, excluding library repetitions) and #occurrences for each check
# Input:
#   - 1: input file (ruleset)
#   - 2: output file (ruleset)
# Output:
#   - Writes the result to (2)


require "csv"
require_relative "graph"

raise "Specify input/output file and the graph dump folder" if ARGV.size < 2

INFILE      = ARGV[0]
OUTFILE     = ARGV[1]
G_FOLDER    = ARGV[2]
DETAILED    = ARGV[3] == "y"

raise "Output file already exists"      if FileTest.exists? OUTFILE
raise "Input file does not exist"       unless FileTest.exists? INFILE

INHERIT_IF_HIGHER_CONFIDENCE = true

class Array
    def >(oth)
        oth.each do |e|
            return false unless self.include? e
        end
    end
    
    def <(oth)
        return oth > self
    end
end

class Set
    def initialize
        @hash = {}
    end
    
    def add(v)
        @hash[v] = true
    end
    
    def remove(v)
        @hash.delete v
    end
    
    def -(oth)
        oth.each do |v|
            remove v
        end
    end
    
    def +(oth)
        oth.each do |v|
            add v
        end
    end
    
    def values
        return @hash.values
    end
    
    def clear
        @hash.clear
    end
    
    def each
        @hash.keys.each do |v|
            yield v
        end
    end
    
    def include?(v)
        return @hash[v]
    end
end

class RuleNode
    attr_accessor   :comparison
    attr_accessor   :version
    attr_accessor   :true_apis
    attr_accessor   :false_apis
    attr_accessor   :apps
    attr_accessor   :message
    
    def initialize(comparison, version, true_apis, false_apis, apps, message)
        @comparison     = comparison
        @version        = version
        @true_apis      = true_apis
        @false_apis     = false_apis
        @apps           = apps
        @message        = message
    end
    
    def <(oth)
        return false if @comparison != oth.comparison || @version != oth.version
        
        if @true_apis < oth.true_apis && @false_apis < oth.false_apis
            # We do not consider the empty set as a subset of any other set.
            return false if @true_apis.size == 0  && oth.true_apis != 0
            return false if @false_apis.size == 0 && oth.false_apis != 0
            
            return false if INHERIT_IF_HIGHER_CONFIDENCE && self.apps.size < oth.apps.size
            
            return true
        else
            return false
        end
    end
    
    def to_s
        tapi = @true_apis#.map {|v| v.split(".")[-1].split("(")[0]}
        fapi = @false_apis#.map {|v| v.split(".")[-1].split("(")[0]}
        result = @apps.size.to_s + "\\n" + tapi.join("\\n") + "\\n\\n~~~\\n\\n" + fapi.join("\\n")
        
        if result.size > 16383
            result = result[0, 16380] + "..."
        end
        
        return result
    end
end

class RuleDAG < DAG
    def initialize(name)
        super
        @confidences = Set.new
    end
    
    def add_rule(rule)
        self.add_vertex rule
        
        self.each_vertex do |r2|
            if rule != r2
                self.add_edge rule, r2 if rule < r2
                self.add_edge r2, rule if r2 < rule
            end
        end
    end
    
    def add_edge(from, to)
        super
        
        @confidences.clear
    end
    
    def get_confidence_of(rule)
        return compute_support_apps(rule).size
    end
    
    def compute_support_apps(rule)
        apps = Set.new
        apps += rule.apps
        
        self.get_edges_from(rule).each do |oth|
            apps += oth.apps
        end
        
        return apps
    end
    
    def biggest_tree
        best_size = 0
        best      = nil
        
        self.each_vertex do |r|
            current_size = get_edges_from(r).size
            if current_size > best_size
                best_size = current_size
                best = r
            end
        end
        
        result = RuleDAG.new self.name
        result.add_rule best
        self.get_edges_from(best).each do |r|
            result.add_rule r
        end
        
        return result
    end
end

dags = {}
CSV.parse(File.read(INFILE), {headers: true, col_sep: ","}) do |row|
    comparison  = row["comparison"]
    version     = row["version"]
    true_apis   = row["true_apis"] != ""    ? row["true_apis"].split("&")   : []
    false_apis  = row["false_apis"] != ""   ? row["false_apis"].split("&")  : []
    apps        = row["appset"].split("&")
    message     = row["keywords"]
    
    key = comparison + " " + version
        
    dags[key] = RuleDAG.new(key) unless dags[key]
    dags[key].add_rule RuleNode.new(comparison, version, true_apis, false_apis, apps, message)
end

CSV.open OUTFILE, "w" do |csv|
    if DETAILED
        csv << ["comparison", "version", "true_apis", "false_apis", "napps", "support"]
    else
        csv << ["comparison", "version", "true_apis", "false_apis", "napps", "keywords"]
    end
    dags.values.each do |dag|
        dag.each_root_vertex do |rule|
            confidence = dag.get_confidence_of(rule)
            if DETAILED
                last = dag.compute_support_apps(rule).join("&")
            else
                last = rule.message
            end
            
            csv << [rule.comparison, rule.version, rule.true_apis.join("&"), rule.false_apis.join("&"), confidence, last]
        end
    end
end

if G_FOLDER && G_FOLDER.length > 0
    puts "OK, dumping the graphs..."
    dags.each do |key, dag|
        begin
            dag.dump(File.join(G_FOLDER, key.gsub("<=", "le").gsub("!=", "ne") + ".dot"))
        rescue
            p $!
        end
        
        dag.biggest_tree.dump(File.join(G_FOLDER, "biggest_" + key.gsub("<=", "le").gsub("!=", "ne") + ".dot"))
    end
end
