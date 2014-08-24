package de.ugoe.cs.cpdp.training;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.io.output.NullOutputStream;

import de.ugoe.cs.util.console.Console;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.clusterers.EM;
import weka.core.DenseInstance;
import weka.core.EuclideanDistance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

/**
 * ACHTUNG UNFERTIG
 *
 */
public class WekaLocalTraining2 extends WekaBaseTraining2 implements ITrainingStrategy {
	
	private final TraindatasetCluster classifier = new TraindatasetCluster();
	private final QuadTree q = null;
	private final Fastmap f = null;
	
	/*Stopping rule for tree reqursion (Math.sqrt(Instances)*/
	public static double ALPHA = 3;
	/*Stopping rule for clustering*/
	public static double DELTA = 0.5;
	/*size of the complete set (used for density)*/
	public static int SIZE = 0;
	
	// cluster
	private static ArrayList<ArrayList<QuadTreePayload<Instance>>> cluster = new ArrayList<ArrayList<QuadTreePayload<Instance>>>();
	
	@Override
	public Classifier getClassifier() {
		return classifier;
	}
	
	
	@Override
	public void apply(Instances traindata) {
		PrintStream errStr	= System.err;
		System.setErr(new PrintStream(new NullOutputStream()));
		try {
			classifier.buildClassifier(traindata);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			System.setErr(errStr);
		}
	}
	
	
	public class TraindatasetCluster extends AbstractClassifier {
		
		private static final long serialVersionUID = 1L;

		private EM clusterer = null;

		private HashMap<Integer, Classifier> cclassifier = new HashMap<Integer, Classifier>();
		private HashMap<Integer, Instances> ctraindata = new HashMap<Integer, Instances>(); 
		
		
		
		private Instance createInstance(Instances instances, Instance instance) {
			// attributes for feeding instance to classifier
			Set<String> attributeNames = new HashSet<>();
			for( int j=0; j<instances.numAttributes(); j++ ) {
				attributeNames.add(instances.attribute(j).name());
			}
			
			double[] values = new double[instances.numAttributes()];
			int index = 0;
			for( int j=0; j<instance.numAttributes(); j++ ) {
				if( attributeNames.contains(instance.attribute(j).name())) {
					values[index] = instance.value(j);
					index++;
				}
			}
			
			Instances tmp = new Instances(instances);
			tmp.clear();
			Instance instCopy = new DenseInstance(instance.weight(), values);
			instCopy.setDataset(tmp);
			
			return instCopy;
		}
		
		
		@Override
		public double classifyInstance(Instance instance) {
			double ret = 0;
			try {
				Instances traindata = ctraindata.get(0);
				Instance classInstance = createInstance(traindata, instance);
				
				// remove class attribute before clustering
				Remove filter = new Remove();
				filter.setAttributeIndices("" + (traindata.classIndex() + 1));
				filter.setInputFormat(traindata);
				traindata = Filter.useFilter(traindata, filter);
				
				Instance clusterInstance = createInstance(traindata, instance);
				
				// 1. classify testdata instance to a cluster number
				int cnum = clusterer.clusterInstance(clusterInstance);
				
				// 2. classify testata instance to the classifier
				ret = cclassifier.get(cnum).classifyInstance(classInstance);
				
			}catch( Exception e ) {
				Console.traceln(Level.INFO, String.format("ERROR matching instance to cluster!"));
				throw new RuntimeException(e);
			}
			return ret;
		}

		
		
		@Override
		public void buildClassifier(Instances traindata) throws Exception {
			
			// 1. copy traindata
			Instances train = new Instances(traindata);
			
			// 2. remove class attribute for clustering
			Remove filter = new Remove();
			filter.setAttributeIndices("" + (train.classIndex() + 1));
			filter.setInputFormat(train);
			train = Filter.useFilter(train, filter);
			
			
			// 3. calculate distance matrix
			EuclideanDistance d = new EuclideanDistance(train);
			double[][] dist = new double[train.size()][train.size()];
			for(int i=0; i < train.size(); i++) {
				for(int j=0; j < train.size(); j++) {
					dist[i][j] = d.distance(train.get(i), train.get(j));
				}
			}
			
			// 4. run fastmap for 2 dimensions on distance matrix
			Fastmap f = new Fastmap(2, dist);
			f.calculate(2);
			double[][] X = f.getX();
			
			// quadtree payload generation
			ArrayList<QuadTreePayload<Instance>> qtp = new ArrayList<QuadTreePayload<Instance>>();
		    
			// die max und min brauchen wir für die größenangaben der sektoren
			double[] big = {0,0};
			double[] small = {-99999,-99999};
			
			// set quadtree payload values
		    for(int i=0; i<X.length; i++){
		    	if(X[i][0] >= big[0]) {
		    		big[0] = X[i][0];
		    	}
		    	if(X[i][1] >= big[1]) {
		    		big[1] = X[i][1];
		    	}
		    	if(X[i][0] <= small[0]) {
		    		small[0] = X[i][0];
		    	}
		    	if(X[i][1] <= small[1]) {
		    		small[1] = X[i][1];
		    	}
		        QuadTreePayload<Instance> tmp = new QuadTreePayload<Instance>(X[i][0], X[i][1], train.get(i));
		        qtp.add(tmp);
		    }
		    
		    // 5. generate quadtree
		    QuadTree q = new QuadTree(null, qtp);
		    ALPHA = Math.sqrt(train.size());
		    SIZE = train.size();
		    
		    // split recursively
		    q.setSize(new double[] {small[0], big[0]}, new double[] {small[1], big[1]});
		    q.recursiveSplit(q);
		    
		    // generate list of nodes sorted by density
		    ArrayList<QuadTree> l = new ArrayList<QuadTree>(q.getList(q));
		    
		    // grid clustering recursive (tree pruning)
		    q.gridClustering(l);
		    
		    
		    // hier müssten wir sowas haben wie welche instanz in welchem cluster ist
		    // oder wir iterieren durch die cluster und sammeln uns die instaznen daraus
		    for(int i=0; i < cluster.size(); i++) {
		    	ArrayList<QuadTreePayload<Instance>> current = cluster.get(i);
		    	for(int j=0; j < current.size(); j++ ) {
		    		
		    	}
		    }
		    
			Instances ctrain = new Instances(train);
			
			// get traindata per cluster
			int cnumber;
			for ( int j=0; j < ctrain.numInstances(); j++ ) {
				// get the cluster number from the attributes, subract 1 because if we clusterInstance we get 0-n, and this is 1-n
				//cnumber = Integer.parseInt(ctrain.get(j).stringValue(ctrain.get(j).numAttributes()-1).replace("cluster", "")) - 1;
				
				cnumber = clusterer.clusterInstance(ctrain.get(j));
				// add training data to list of instances for this cluster number
				if ( !ctraindata.containsKey(cnumber) ) {
					ctraindata.put(cnumber, new Instances(traindata));
					ctraindata.get(cnumber).delete();
				}
				ctraindata.get(cnumber).add(traindata.get(j));
			}
			
			// train one classifier per cluster, we get the clusternumber from the traindata
			Iterator<Integer> clusternumber = ctraindata.keySet().iterator();
			while ( clusternumber.hasNext() ) {
				cnumber = clusternumber.next();			
				cclassifier.put(cnumber,setupClassifier());
				cclassifier.get(cnumber).buildClassifier(ctraindata.get(cnumber));
				
				//Console.traceln(Level.INFO, String.format("classifier in cluster "+cnumber));
			}
		}
	}
	

	/**
	 * hier stecken die Fastmap koordinaten drin
	 * sowie als Payload jeweils 1 weka instanz
	 */
	public class QuadTreePayload<T> {

		public double x;
		public double y;
		private T inst;
		
		public QuadTreePayload(double x, double y, T value) {
			this.x = x;
			this.y = y;
			this.inst = value;
		}
		
		public T getinst() {
			return this.inst;
		}
	}
	
	/**
	 * Fastmap implementation
	 * 
	 * Faloutsos, C., & Lin, K. I. (1995). 
	 * FastMap: A fast algorithm for indexing, data-mining and visualization of traditional and multimedia datasets 
	 * (Vol. 24, No. 2, pp. 163-174). ACM.
	 */
	public class Fastmap {
		
		/*N x k Array, at the end, the i-th row will be the image of the i-th object*/
		private double[][] X;
		
		/*2 x k pivot Array one pair per recursive call*/
		private double[][] PA;
		
		/*Objects we got (distance matrix)*/
		private double[][] O;
		
		/*column of X currently updated (also the dimension)*/
		private int col = 0;
		
		public Fastmap(int k, double[][] O) {
			this.O = O;
			
			int N = O.length;
			
			this.X = new double[N][k];
			this.PA = new double[2][k];
		}
		
		
		/*recursive function ALT*/
		private double dist2(int x, int y, int k) {
			// basisfall
			if(k == 0) {
				return Math.pow(this.O[x][y], 2);
			}
			
			double dist_rec = Math.pow(this.dist(x, y, k-1), 2);
			double dist_norm = Math.pow(Math.abs(this.X[x][k] - this.X[y][k]), 2); 
			
			return Math.sqrt(Math.abs(dist_rec - dist_norm));
			//return Math.abs(dist_rec - dist_norm);
		}
		
		/**
		 * The distance function for eculidean distance
		 * 
		 * Acts according toequation 4 of the fastmap paper
		 *  
		 * @param x x index of x image (if k==0 x object)
		 * @param y y index of y image (if k==0 y object)
		 * @param kdimensionality
		 * @return distance
		 */
		private double dist(int x, int y, int k) {
			
			// objectabstand ist basis
	
			// das hier wäre ein abstand zwischen 2 weka instanzen, z.B. euclidischer abstand zwischen den beiden vektoren
			double tmp = this.O[x][y] * this.O[x][y]; 
			
			
			// decrease by projections
			for(int i=0; i < k; i++) {
				//double tmp2 = Math.abs(this.X[x][i] - this.X[y][i]);
				double tmp2 =  (this.X[x][i] - this.X[y][i]);
				tmp -= tmp2 * tmp2;
			}
			
			return Math.abs(tmp);
		}
		
		/**
		 * Find the object furthest from the given index
		 * This method is a helper Method for findDistandObjects
		 * 
		 * @param index of the object 
		 * @return index of the furthest object from the given index
		 */
		private int findFurthest(int index) {
			double furthest = -1000000;
			int ret = 0;
			
			for(int i=0; i < O.length; i++) {
				double dist = this.dist(i, index, this.col);
				if(i != index && dist > furthest) {
					furthest = dist;
					ret = i;
				}
			}
			return ret;
		}
		
	
		/**
		 * Finds the pivot objects 
		 * 
		 * This method is basically algorithm 1 of the fastmap paper.
		 * 
		 * @return 2 indexes of the choosen pivot objects
		 */
		private int[] findDistantObjects() {
			// 1. choose object randomly
			Random r = new Random();
			int obj = r.nextInt(this.O.length);
			
			// 2. find furthest object from randomly chooen object
			int idx1 = this.findFurthest(obj);
			
			// 3. find furthest object from previously furthest object
			int idx2 = this.findFurthest(idx1);
			
			int[] ret = {idx1, idx2};
			return ret;
		}
	
		
		/**
		 * Gives image of object (projection on the line between px, py)
		 * 
		 * @param index of the object to project
		 * @param px pivot 1
		 * @param py pivot 2
		 * @return projection
		 */
		private double project(int index, int px, int py) {
			
			double dix = this.dist(index, px, this.col);
			double diy = this.dist(index, py, this.col);
			double dxy = this.dist(px, py, this.col);
			
			return (dix + dxy - diy) / 2 * Math.sqrt(dxy);
		}
	
	
		/*recursive function ALT, geht auch sequentiell*/
		public void calculate2(int k) {
			
			// 1) basisfall
			if(k <= 0) {
				return;
			}
			
			// 2) choose pivot objects
			int[] pivots = this.findDistantObjects();
			
			// 3) record ids of pivot objects 
			this.PA[0][this.col] = pivots[0];
			this.PA[1][this.col] = pivots[1];
			
			System.out.println("found pivots with index: " + pivots[0] + ","+ pivots[1]);
			
			// 4) inter object distances are zero (this.X is initialized with 0 so we just return)
			if(this.dist(pivots[0], pivots[1], this.col) == 0) {
				return;
			}
			
			double dxy = this.dist(pivots[0], pivots[1], this.col);
			
			if(dxy == 0) {
				return;
			}
			
			// 5) project the objects on the line between the pivots
			for(int i=0; i < this.O.length; i++) {
				
				double dix = this.dist(i, pivots[0], this.col);
				double diy = this.dist(i, pivots[1], this.col);
	
				this.X[i][this.col] = (dix + dxy - diy) / 2 * Math.sqrt(dxy);
				
				//this.X[i][this.col] = this.project(i, pivots[0], pivots[1]);
			}
			
			this.col += 1;
			
			// 6) recurse
			this.calculate2(k-1);
		}
		
		
		// test funktion, reproduziert ergebnisse aus dem technical report von fastmap
		public int[] findDistantObjects2() {
			int[] ret = {0,0};
			if(this.col == 0) {
				ret = new int[] {0,3};
			}
			if(this.col == 1) {
				ret = new int[] {4,1};
			}
			if(this.col == 2) {
				ret = new int[] {2,4};
			}
			
			return ret;
		}
		
		
		/**
		 * Calculates the new k-vector values
		 * 
		 * @param dims dimensionality
		 */
		public void calculate(int dims) {
			
			for(int k=0; k <dims; k++) {
				
				// 2) choose pivot objects
				int[] pivots = this.findDistantObjects();
				
				// 3) record ids of pivot objects 
				this.PA[0][this.col] = pivots[0];
				this.PA[1][this.col] = pivots[1];
				
				// 4) inter object distances are zero (this.X is initialized with 0 so we just continue)
				if(this.dist(pivots[0], pivots[1], this.col) == 0) {
					continue;
				}
				
				// 5) project the objects on the line between the pivots
				double dxy = this.dist(pivots[0], pivots[1], this.col);
				for(int i=0; i < this.O.length; i++) {
					
					double dix = this.dist(i, pivots[0], this.col);
					double diy = this.dist(i, pivots[1], this.col);
		
					double tmp = (dix + dxy - diy) / 2 * Math.sqrt(dxy);
					
					this.X[i][this.col] = tmp; // / 10000; 
					//this.X[i][this.col] = this.project(i, pivots[0], pivots[1]);
				}
				
				this.col += 1;
			}
		}
		
		
		/**
		 * returns the result matrix
		 * @return calculated result
		 */
		public double[][] getX() {
			return this.X;
		}
	}


	public class QuadTree {
		
		// 1 parent or null
		private QuadTree parent = null;
		
		// 4 childs, 1 per quadrant
		private QuadTree child_nw;
		private QuadTree child_ne;
		private QuadTree child_se;
		private QuadTree child_sw;
		
		// list (only helps with generate list of childs!)
		private ArrayList<QuadTree> l = new ArrayList<QuadTree>();
		

		public int level = 0;
		
		// size of the quadrant
		private double[] x;
		private double[] y;
		
		public boolean verbose = false;
		
		// payload, mal sehen ob das geht mit dem generic
		// evtl. statt ArrayList eigene QuadTreePayloadlist
		private ArrayList<QuadTreePayload<Instance>> payload;
		
		
		public QuadTree(QuadTree parent, ArrayList<QuadTreePayload<Instance>> payload) {
			this.parent = parent;
			this.payload = payload;
		}
		
		
		public String toString() {
			String n = "";
			if(this.parent == null) {
				n += "rootnode ";
			}
			String level = new String(new char[this.level]).replace("\0", "-");
			n += level + " instances: " + this.getNumbers();
			return n;
		}
		
		
		/**
		 * Returns the payload, used for clustering
		 * in the clustering list we only have children with paylod
		 * 
		 * @return payload
		 */
		public ArrayList<QuadTreePayload<Instance>> getPayload() {
			return this.payload;
		}
		
		/**
		 * Calculate the density of this quadrant
		 * 
		 * density = number of instances / global size (all instances)
		 * 
		 * @return
		 */
		public double getDensity() {
			double dens = 0;
			dens = (double)this.getNumbers() / SIZE;
			return dens;
		}
		
		public void setSize(double[] x, double[] y){
			this.x = x;
			this.y = y;
		}
		
		public double[][] getSize() {
			return new double[][] {this.x, this.y}; 
		}
		
		
		/**
		 * Todo: dry, median ist immer dasselbe
		 *  
		 * @return median for x
		 */
		private double getMedianForX() {
			double med_x =0 ;
			
			Collections.sort(this.payload, new Comparator<QuadTreePayload<Instance>>() {
		        @Override
		        public int compare(QuadTreePayload<Instance> x1, QuadTreePayload<Instance> x2) {
		            return Double.compare(x1.x, x2.x);
		        }
		    });
	
			if(this.payload.size() % 2 == 0) {
				int mid = this.payload.size() / 2;
				med_x = (this.payload.get(mid).x + this.payload.get(mid+1).x) / 2;
			}else {
				int mid = this.payload.size() / 2;
				med_x = this.payload.get(mid).x;
			}
			
			if(this.verbose) {
				System.out.println("sorted:");
				for(int i = 0; i < this.payload.size(); i++) {
					System.out.print(""+this.payload.get(i).x+",");
				}
				System.out.println("median x: " + med_x);
			}
			return med_x;
		}
		
		
		private double getMedianForY() {
			double med_y =0 ;
			
			Collections.sort(this.payload, new Comparator<QuadTreePayload<Instance>>() {
		        @Override
		        public int compare(QuadTreePayload<Instance> y1, QuadTreePayload<Instance> y2) {
		            return Double.compare(y1.y, y2.y);
		        }
		    });
			
			if(this.payload.size() % 2 == 0) {
				int mid = this.payload.size() / 2;
				med_y = (this.payload.get(mid).y + this.payload.get(mid+1).y) / 2;
			}else {
				int mid = this.payload.size() / 2;
				med_y = this.payload.get(mid).y;
			}
			
			if(this.verbose) {
				System.out.println("sorted:");
				for(int i = 0; i < this.payload.size(); i++) {
					System.out.print(""+this.payload.get(i).y+",");
				}
				System.out.println("median y: " + med_y);
			}
			return med_y;
		}
		
		
		/**
		 * Reurns the number of instances in the payload
		 * 
		 * @return int number of instances
		 */
		public int getNumbers() {
			int number = 0;
			if(this.payload != null) {
				number = this.payload.size();
			}
			return number;
		}
		
		
		/**
		 * Calculate median values of payload for x, y and split into sectors
		 * 
		 * @return Array of QuadTree nodes (4 childs)
		 * @throws Exception if we would run into an recursive loop
		 */
		public QuadTree[] split() throws Exception {
					
			double medx = this.getMedianForX();
			double medy = this.getMedianForY();
			
			
			ArrayList<QuadTreePayload<Instance>> nw = new ArrayList<QuadTreePayload<Instance>>();
			ArrayList<QuadTreePayload<Instance>> sw = new ArrayList<QuadTreePayload<Instance>>();
			ArrayList<QuadTreePayload<Instance>> ne = new ArrayList<QuadTreePayload<Instance>>();
			ArrayList<QuadTreePayload<Instance>> se = new ArrayList<QuadTreePayload<Instance>>();
			
			// sort the payloads to new payloads
			// here we have the problem that payloads with the same values are sorted
			// into the same slots and it could happen that medx and medy = size_x[1] and size_y[1]
			// in that case we would have an endless loop
			for(int i=0; i < this.payload.size(); i++) {
				
				QuadTreePayload<Instance> item = this.payload.get(i);
				
				// north west
				if(item.x <= medx && item.y >= medy) {
					nw.add(item);
				}
				
				// south west
				else if(item.x <= medx && item.y <= medy) {
					sw.add(item);
				}
	
				// north east
				else if(item.x >= medx && item.y >= medy) {
					ne.add(item);
				}
				
				// south east
				else if(item.x >= medx && item.y <= medy) {
					se.add(item);
				}
			}
			
			// if we assign one child a payload equal to our own (see problem above)
			// we throw an exceptions which stops the recursion on this node
			if(nw.equals(this.payload)) {
				throw new Exception("payload equal");
			}
			if(sw.equals(this.payload)) {
				throw new Exception("ayload equal");
			}
			if(ne.equals(this.payload)) {
				throw new Exception("payload equal");
			}
			if(se.equals(this.payload)) {
				throw new Exception("payload equal");
			}
			
			this.child_nw = new QuadTree(this, nw);
			this.child_nw.setSize(new double[] {this.x[0], medx}, new double[] {medy, this.y[1]});
			this.child_nw.level = this.level + 1;
			
			this.child_sw = new QuadTree(this, sw);
			this.child_sw.setSize(new double[] {this.x[0], medx}, new double[] {this.y[0], medy});
			this.child_sw.level = this.level + 1;
			
			this.child_ne = new QuadTree(this, ne);
			this.child_ne.setSize(new double[] {medx, this.x[1]}, new double[] {medy, this.y[1]});
			this.child_ne.level = this.level + 1;
			
			this.child_se = new QuadTree(this, se);
			this.child_se.setSize(new double[] {medx, this.x[1]}, new double[] {this.y[0], medy});
			this.child_se.level = this.level + 1;
			
			this.payload = null;
			return new QuadTree[] {this.child_nw, this.child_ne, this.child_se, this.child_sw};
		}
		
		
		/** 
		 * Todo: evt. auslagern, eigentlich auch eher ne statische methode
		 * 
		 * @param q
		 */
		public void recursiveSplit(QuadTree q) {
			if(this.verbose) {
				System.out.println("splitting: "+ q);
			}
			if(q.getNumbers() < ALPHA) {
				return;
			}else{
				// exception wird geworfen wenn es zur endlosrekursion kommen würde (siehe text bei split())
				try {
					QuadTree[] childs = q.split();			
					this.recursiveSplit(childs[0]);
					this.recursiveSplit(childs[1]);
					this.recursiveSplit(childs[2]);
					this.recursiveSplit(childs[3]);
				}catch(Exception e) {
					return;
				}
			}
		}
		
		
		/**
		 * returns an list of childs sorted by density
		 * 
		 * @param q QuadTree
		 * @return list of QuadTrees
		 */
		private void generateList(QuadTree q) {
			
			// entweder es gibtes 4 childs oder keins
			if(q.child_ne == null) {
				this.l.add(q);
				//return;
			}
			
			if(q.child_ne != null) {
				this.generateList(q.child_ne);
			}
			if(q.child_nw != null) {
				this.generateList(q.child_nw);
			}
			if(q.child_se != null) {
				this.generateList(q.child_se);
			}
			if(q.child_sw != null) {
				this.generateList(q.child_sw);
			}
		}
		
		
		/**
		 * Checks if passed QuadTree is neighbouring to us
		 * 
		 * @param q QuadTree
		 * @return true if passed QuadTree is a neighbour
		 */
		public boolean isNeighbour(QuadTree q) {
			boolean is_neighbour = false;
			
			double[][] our_size = this.getSize();
			double[][] new_size = q.getSize();
			
			// X is i=0, Y is i=1
			for(int i =0; i < 2; i++) {
				// check X and Y (0,1)
				// we are smaller than q
				// -------------- q
				//    ------- we
				if(our_size[i][0] >= new_size[i][0] && our_size[i][1] <= new_size[i][1]) {
					is_neighbour = true;
				}
				// we overlap with q at some point
				//a) ---------------q
				//         ----------- we
				//b)     --------- q
				// --------- we
				if((our_size[i][0] >= new_size[i][0] && our_size[i][0] <= new_size[i][1]) ||
				   (our_size[i][1] >= new_size[i][0] && our_size[i][1] <= new_size[i][1])) {
					is_neighbour = true;
				}
				// we are larger than q
				//    ---- q
				// ---------- we
				if(our_size[i][1] >= new_size[i][1] && our_size[i][0] <= new_size[i][0]) {
					is_neighbour = true;
				}
			}
			
			if(is_neighbour && this.verbose) {
				System.out.println(this + " neighbour of: " + q);
			}
			
			return is_neighbour;
		}
		
		
		
		/**
		 * Perform Pruning and clustering of the quadtree
		 * 
		 * 1) get list of leaf quadrants
		 * 2) sort by their density
		 * 3) merge similar densities to new leaf quadrant
		 * @param q QuadTree
		 */
		public void gridClustering(ArrayList<QuadTree> list) {
			
			//System.out.println("listsize: " + list.size());
			
			// basisfall
			if(list.size() == 0) {
				return;
			}
			
			double stop_rule;
			QuadTree biggest;
			QuadTree current;
			
			// current clusterlist
			ArrayList<QuadTreePayload<Instance>> current_cluster;
	
			// remove list
		    ArrayList<Integer> remove = new ArrayList<Integer>();
			
			// 1. find biggest
		    biggest = list.get(list.size()-1);
		    stop_rule = biggest.getDensity() * 0.5;
		    
		    current_cluster = new ArrayList<QuadTreePayload<Instance>>();
		    current_cluster.addAll(biggest.getPayload());
		    //System.out.println("adding "+biggest.getDensity() + " to cluster");
		    
		    // remove the biggest because we are starting with it
		    remove.add(list.size()-1);
		    //System.out.println("removing "+biggest.getDensity() + " from list");
		    
			// while items in list
		    for(int i=list.size()-1; i >= 0; i--) {
		    	current = list.get(i);
		    	
				// 2. find neighbours with correct density
		    	// if density > 0.5 * DELTA and is_neighbour add to cluster
		    	if(current.getDensity() > stop_rule && !current.equals(biggest) && current.isNeighbour(biggest)) {
		    		//System.out.println("adding " + current.getDensity() + " to cluster");
		    		//System.out.println("removing "+current.getDensity() + " from list");
		    		current_cluster.addAll(current.getPayload());
		    		
		    		// wir können hier nicht removen weil wir sonst den index verschieben
		    		remove.add(i);
		    	}
			}
		    
			// 3. remove from list
		    for(Integer item: remove) {
		    	list.remove((int)item);
		    }
		    
			// 4. add to cluster
		    cluster.add(current_cluster);
			
			// recurse
		    //System.out.println("restlist " + list.size());
		    this.gridClustering(list);
		}
		
		public void printInfo() {
		    System.out.println("we have " + cluster.size() + " clusters");
		    
		    for(int i=0; i < cluster.size(); i++) {
		    	System.out.println("cluster: "+i+ " size: "+ cluster.get(i).size());
		    }
		}
		
		/**
		 * 
		 * @param q
		 * @return
		 */
		public ArrayList<QuadTree> getList(QuadTree q) {
			this.generateList(q);
			
			Collections.sort(this.l, new Comparator<QuadTree>() {
		        @Override
		        public int compare(QuadTree x1, QuadTree x2) {
		            return Double.compare(x1.getDensity(), x2.getDensity());
		        }
		    });
			
			return this.l;
		}
	}
}