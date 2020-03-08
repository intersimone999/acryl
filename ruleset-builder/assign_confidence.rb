# Aggregates the api file, measuring reliability (#apps) and #occurrences for each check
# Input:
#   - 1: input file (matched)
#   - 2: API file
#   - 3: output file
# Output:
#   - Writes the result to (3)


require "csv"
require "set"

raise "Specify ruleset, API folder and output file"   if ARGV.size < 3

RULESET   = ARGV[0]
APIFILE   = ARGV[1]
OUTFILE   = ARGV[2]

SPECIAL_K = 1.0

raise "Output file already exists"      if FileTest.exists? OUTFILE
raise "Ruleset file does not exist"     unless FileTest.exists? RULESET
raise "API file does not exist"         unless FileTest.exists? APIFILE

#api -> #apps
apidata = {}
CSV.parse(File.read(APIFILE), {headers: true, col_sep: ","}) do |row|
    api         = row["api"]
    apps        = Set.new(row["apps"].split("&"))
        
    apidata[api] = apps
end

all_apps = Set.new(apidata.values.flatten.uniq)

errors = 0
CSV.open OUTFILE, "w" do |csv|
    csv << ["comparison", "version", "true_apis", "false_apis", "napps", "keywords"]
    CSV.parse(File.read(RULESET), {headers: true, col_sep: ","}) do |row|
        comparison  = row["comparison"]
        version     = row["version"]
        true_apis   = row["true_apis"]
        false_apis  = row["false_apis"]
        napps       = row["napps"].to_i
        keywords    = row["keywords"]
        
        # Set of APIs appearing in the true branch of the rule
        true_apis_list  = true_apis.split("&")
        # Set of APIs appearing in the false branch of the rule
        false_apis_list = false_apis.split("&")
        
        # Compute true_appears_in, i.e. the set of apps in which the true set of APIs appears
#         true_appears_in = all_apps.dup
#         true_apis_list.each do |api|
#             raise "API #{api} not existing!" unless apidata[api]
#             
#             true_appears_in &= apidata[api]
#         end
#         
#         
#         # Compute false_appears_in, i.e. the set of apps in which the false set of APIs appears
#         false_appears_in = all_apps.dup
#         false_apis_list.each do |api|
#             raise "API #{api} not existing!" unless apidata[api]
#             
#             false_appears_in &= apidata[api]
#         end
#         
#         # Number of apps in which true or false api sets appear
#         nboth  = [(true_appears_in | false_appears_in).size, 1].max
#         # Number of apps in which true apis appear
#         ntrue  = true_appears_in.size
#         # Number of apps in which false apis appear
#         nfalse = false_appears_in.size
#         
#         errors += 1 if nboth == 0
        
#         reliability         = napps.to_f / nboth
#         reliability_direct  = napps.to_f / ntrue
#         reliability_inverse = napps.to_f / nfalse
        
        ntrue  = true_apis_list.map  { |a| apidata[a] ? apidata[a].size : 0 }.min || 0
        nfalse = false_apis_list.map { |a| apidata[a] ? apidata[a].size : 0 }.min || 0
        nboth  = [[ntrue, nfalse].min, 1].max
        confidence = napps * (1 + SPECIAL_K * (1 - (nboth.to_f / all_apps.size)))
        
        csv << [comparison, version, true_apis, false_apis, confidence, keywords]
    end
end

raise "Wrong postcondition: number of apps implementing at least one of the apis sets is empty for #{errors} cases" if errors > 0
