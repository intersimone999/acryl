class Node
    attr_reader     :object
    def initialize(object)
        @object = object
    end
end

class Edge
end

class DAG
    attr_reader         :name
    def initialize(name)
        @nodes      = {}
        @edges_from = {}
        @edges_to   = {}
        @edges      = {}
        
        @name = name
    end
    
    def add_edge(from, to, edge = Edge.new)
        assert_vertex from
        assert_vertex to
        
        @edges_from[from] = {} unless @edges_from[from]
        @edges_from[from][to] = true
        
        @edges_to[to] = {} unless @edges_to[to]
        @edges_to[to][from] = true
        
        @edges[[from, to]] = edge
    end
    
    def remove_edge(from, to)
        @edges_from[from].delete to
        @edges_to[to].delete from
        @edges.delete [from, to]
    end
    
    def add_vertex(v)
        @nodes[v] = true
    end
    
    def get_edges_from(from)
        if @edges_from[from]
            return @edges_from[from].keys
        else
            return []
        end
    end
    
    def get_edges_to(to)
        if @edges_to[to]
            return @edges_to[to].keys
        else
            return []
        end
    end
    
    def each_vertex
        @nodes.keys.each do |v|
            yield v
        end
    end
    
    def each_root_vertex
        @nodes.keys.select {|n| !@edges_to[n] || @edges_to[n].size == 0}.each do |v|
            yield v
        end
    end
    
    def each_edge
        @edges.each do |key, edge|
            from, to = *key
            yield from, to, edge
        end
    end
    
    def dump(filename)
        raise "File already existing" if FileTest.exist? filename
        File.open(filename, "w") do |f| 
            f.puts "digraph {"
            self.each_vertex do |v|
                f.puts "\tv#{v.object_id} [label=\"#{v.to_s}\", shape=box]"
            end
            
            self.each_edge do |from, to|
                f.puts "\tv#{from.object_id} -> v#{to.object_id}"
            end
            f.puts "}"
        end
    end
    
    private
    def assert_vertex(v)
        raise "Vertex #{v} is not in the graph!" unless @nodes[v]
    end
end
