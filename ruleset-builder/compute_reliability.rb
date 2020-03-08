# Aggregates the api file, measuring reliability (#apps) and #occurrences for each check
# Input:
#   - 1: input file (matched)
#   - 2: output file
# Output:
#   - Writes the result to (2)


require "csv"

raise "Specify ruleset, apifile and output file"   if ARGV.size < 3

RULESET   = ARGV[0]
APIFILE   = ARGV[1]
OUTFILE   = ARGV[2]
# Set to true only if you want force the output, without detecting errors (otherwise, do not use it)
EASY      = ARGV[3] == "easy"

raise "Output file already exists"      if FileTest.exists? OUTFILE
raise "Ruleset file does not exist"     unless FileTest.exists? RULESET
raise "Apifile file does not exist"     unless FileTest.exists? APIFILE

#api -> #apps
apidata = {}
CSV.parse(File.read(APIFILE), {headers: true, col_sep: ","}) do |row|
    api         = row["api"]
    apps        = row["apps"].split("&")
        
    apidata[api] = apps
end


CSV.open OUTFILE, "w" do |csv|
    csv << ["comparison", "version", "true_apis", "false_apis", "occurrences", "napps", "reliability", "reliability_direct", "reliability_inverse"]
    CSV.parse(File.read(RULESET), {headers: true, col_sep: ","}) do |row|
        comparison  = row["comparison"]
        version     = row["version"]
        true_apis   = row["true_apis"]
        false_apis  = row["false_apis"]
        occurrences = row["occurrences"].to_i
        napps       = row["reliability"].to_i
        
        # Set of APIs appearing in the true branch of the rule
        true_apis_list  = true_apis.split("&")
        # Set of APIs appearing in the false branch of the rule
        false_apis_list = false_apis.split("&")
        
        # Compute true_appears_in, i.e. the set of apps in which the true set of APIs appears
        true_appears_in = apidata.values.flatten.uniq
        true_apis_list.each do |api|
            next if EASY && !apidata[api]
            
            true_appears_in &= apidata[api]
        end
        
        # Compute false_appears_in, i.e. the set of apps in which the false set of APIs appears
        false_appears_in = apidata.values.flatten.uniq
        false_apis_list.each do |api|
            next if EASY && !apidata[api]
            
            false_appears_in &= apidata[api]
        end
        
        # Number of apps in which true or false api sets appear
        nboth  = (true_appears_in | false_appears_in).size
        # Number of apps in which true apis appear
        ntrue  = true_appears_in.size
        # Number of apps in which false apis appear
        nfalse = false_appears_in.size
        
        nboth  = -1 if EASY && nboth  == 0
        ntrue  = -1 if EASY && ntrue  == 0
        nfalse = -1 if EASY && nfalse == 0
        
        reliability         = napps.to_f / nboth
        reliability_direct  = napps.to_f / ntrue
        reliability_inverse = napps.to_f / nfalse
        
        csv << [comparison, version, true_apis, false_apis, occurrences, napps, reliability, reliability_direct, reliability_inverse]
    end
end
